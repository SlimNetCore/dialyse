---
slice: organisation-o5-retrait-bc-referential
status: planned
date: 2026-11-01
order: 5
context: organisation
sources: [.ddd/tactical/organisation.md, ADR 0063, .ddd/migration/roadmap.md (dette P9)]
---

# O5 — Retrait de `bc-referential` (bascule Organisation O1-O4)

## Intention

Supprimer le module legacy `bc-referential` (domaine `organisation` + `referential` +
`referential/admin`) après bascule de tous ses consommateurs vers le contexte
`organisation` (slices O1-O4, `built`). Les endpoints REST legacy restent stables
pendant la bascule (aucune coupure frontend) : la bascule se fait par ré-implémentation
des adaptateurs, pas par changement d'URL.

## Consommateurs à migrer (inventaire 2026-11-01)

| Consommateur legacy                                                                                                                                                                                           | Port `bc-referential`                                                             | Cible `organisation`                                                                                                                                    |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SocieteRestController` (`/api/v1/societes`, SUPERADMIN, + logos/activer/centres/transfert)                                                                                                                   | `SocieteUseCase`, `SocieteRepositoryPort`                                         | `OrganisationUseCases` + `SocietesControleur`/`CentresControleur`                                                                                       |
| `SocieteRepositoryAdapter`                                                                                                                                                                                    | `SocieteRepositoryPort`                                                           | `SocietesJdbc`, `CentresJdbc`                                                                                                                           |
| `ReferentialRestController` (`/api/v1/referentials/*` : centres-payeurs, agences, caisses, medecins, salles, positions, transporteurs, categories-transport, forfaits, etats-patients, articles, generateurs) | `ReferentialUseCase`, `ReferentialRepositoryPort`                                 | `SallesControleur`, `CreneauxControleur`, `ReferentielsControleur` (medecins/machines/protocoles)                                                       |
| `ReferentialRepositoryAdapter`                                                                                                                                                                                | `ReferentialRepositoryPort`                                                       | idem                                                                                                                                                    |
| `ReferentialAdminApplicationService`, `ReferentialAdminRestController` (`/api/v1/admin/referentials/*`, CRUD générique + import/export + templates)                                                           | `ReferentialAdminUseCase`, `ReferentialAdminRepositoryPort`                       | **ÉCART** : pas de CRUD générique dans le contexte (décision à prendre : réifier ou conserver l'admin générique en platform)                            |
| `ReferentialAdminJdbcAdapter`, `ReferentialAdminTables`                                                                                                                                                       | `ReferentialAdminRepositoryPort`                                                  | idem                                                                                                                                                    |
| `MigrationConfig`, `MigrationApplicationService`, `MigrationRestController`, `MigrationBatchJdbcAdapter`, `MigrationDtos`                                                                                     | `ReferentialAdminRepositoryPort`                                                  | idem (migration de données : à requalifier ou retirer avec le legacy)                                                                                   |
| `ReferentialTemplateWriter`, `TabularFileReader`                                                                                                                                                              | modèles `referential.admin`                                                       | idem                                                                                                                                                    |
| `SocieteRequests`, `SocieteResponse`, `ReferentialKindResponse`, `ReferentialEntryResponse`, `ImportReportResponse`, `ReferentialAdminExceptionHandler`                                                       | DTOs/exceptions                                                                   | à remapper sur les DTOs du contexte                                                                                                                     |
| `DomainServiceConfig` (beans `societeDomainService`, `referentialDomainService`, `referentialAdminDomainService`)                                                                                             | services de domaine                                                               | beans `OrganisationConfiguration` et suivantes                                                                                                          |
| Frontend `societe-api.service.ts`, `societe-admin-api.service.ts`, `referential-api.service.ts`, `referential-admin-api.service.ts`, usages dans `auth-api`, `backend-api`, `direction-api`, `stock-api`      | URLs `/api/v1/societes`, `/api/v1/referentials/*`, `/api/v1/admin/referentials/*` | nouvelles URLs `/api/v1/organisation/*` **ou** maintien des URLs avec ré-implémentation (recommandé : maintenir les URLs, éviter la migration frontend) |

## Écarts de parité (à combler avant cutover)

1. **Logos société** (`SocieteLogoService`, upload/download `/{id}/logo`) : pas d'équivalent
   dans le contexte — à porter (O1 bis) ou à conserver en adapter platform documenté.
2. **Activer/désactiver société et centre, transfert de centre** : vérifier la couverture
   par `OrganisationUseCases` ; sinon porter.
3. **Référentiels non couverts** : centres-payeurs, agences, caisses, transporteurs,
   categories-transport, forfaits, etats-patients, articles, generateurs — décider :
   porter vers Organisation (référentiels partagés) ou vers leurs contextes propriétaires (facturation, stock, gmao…).
4. **Admin générique + import/export + templates + migration de données** : pas de
   published language ; décision structurante requise (ADR) avant cutover.
5. **Cache** : `ref.*` — clés incluant `centerId`, TTL `CACHE_TTL_REFERENTIALS` ; invalider
   à la bascule.

## Séquencement (strangler)

1. **Parité** : porter les écarts 1-4 (slices O1bis/O3bis ou sous-tâches de O5).
2. **Route** : ré-implémenter les adapters platform sur le contexte (mêmes URLs REST),
   double-run en recette (comparer legacy vs contexte sur trafic rejoué).
3. **Cutover** : commuter les beans (`DomainServiceConfig`, `MigrationConfig`) + feature
   flag si besoin ; e2e Playwright (connexion, référentiels, sociétés).
4. **Retire** : supprimer `bc-referential/`, les adapters/DTOs/controllers legacy devenus
   morts, `ReferentialAdminTables` ; vérifier `check-arch` + gardes + `ReglesDeGestionDocumentationTest`
   (retirer les codes d'erreur devenus orphelins de la doc) ; mettre à jour la roadmap P9.

## Données

Mêmes tables (`societes`, `centers`, `salle`, `position_creneau`, référentiels) ; aucune
migration. Garde `TableDeLOrganisationTest` déjà verte et couvrant le périmètre.

## Progression parite (2026-11-01)

- [x] Consulter une societe (GET /api/v1/organisation/societes/{id}) et lister tous les
  centres d'une societe (GET /api/v1/organisation/centres?societeId=) : ajoutes (use cases
  consulterSociete/listerCentres, tests OrganisationServiceTest 4/4 verts,
  SDK frontend OrganisationApiService + specs vitest a jour, check-arch 0 violation).
- [x] Activer/desactiver (societe et centre) : deja couvert via modifierSociete /
  modifierCentre (flag ctif) - pas de nouvel endpoint requis.
- [x] Transfert de centre : deja couvert (POST /centres/{id}/transferer).
- [ ] Logos societe : a porter (O1 bis) ou conserver en adapter platform documente.
- [ ] Liste societes paginee + recherche (legacy : page/size/q) : listerSocietes ()`n retourne la liste complete - a
  paginer avant cutover (regle pagination obligatoire).
- [ ] Referentiels non couverts (centres-payeurs, agences, caisses, transporteurs,
  categories-transport, forfaits, etats-patients, articles, generateurs) : a repartir
  vers Organisation ou leurs contextes proprietaires.
- [ ] Admin generique + import/export + templates + migration de donnees : decision ADR requise.


- [x] Liste societes paginee + recherche (GET /societes?q=&page=&size=, ILIKE compatible
  H2/PostgreSQL, PagedResult) : fait (use case listerSocietesParPage, tests 6/6,
  SDK frontend + specs vitest a jour).
- [x] Logos societe : portes dans le contexte (LogoDeSociete + LogoRefuse + ValidateurImage,
  eleverser/lire/supprimerLogos, societesAvecLogo, endpoints PUT/GET/DELETE + GET /logos,
  422 iolations comme le legacy, SDK frontend + specs). Reste : brancher le rendu dans les
  documents (ModeleDocumentPrinter lit la table societes en direct - hors perimetre O5).
- [x] Articles et generateurs : couverts via langages publies (CatalogueDArticles du Stock,
  GenerateursDuCentre de la GMAO) + ReferentielsLectureService + GET /referentiels/articles,
  /generateurs + SDK frontend.
- [ ] Restent non couverts : centres-payeurs (+details), agences, caisses, transporteurs,
  categories-transport, forfaits, etats-patients (aucun contexte proprietaire ni langage publie).
- [ ] Admin generique + import/export + templates + migration de donnees : decision ADR requise.
- [ ] DTOs serialisables pour les nouveaux endpoints (risque {} Jackson sur objets du domaine
  a accesseurs style record - meme dette que DM7/PD4).


- [x] Modele societe/centre riche (RG-ORG-002 a 005, 008, 010-022) : Coordonnees (nettoyage +
  tel/email/site), NIF/NIS/RC (<=40), pied de page (<=500), code normalise, createdAt,
  pagination plafonnee a 100, Jdbc sur les vraies colonnes (useau_horaire, pas de
  colonne capacite inexistante en base).
- [x] Correction bogue de mapping : CentresJdbc lisait useau/capacite (colonnes
  inexistantes -> 500 en prod) et listerParSociete retournait les seuls actifs ; rouver`n ignorait fuseau_horaire.
  Corrige + CentreTest 6/6.
- [x] Droits a l'oubli : capaciteTheorique supprime du modele (aucune colonne, aucun
  consommateur legacy ; le tactique proposed sera revise en ce sens).
- [x] SDK frontend aligne (types riches + payloads coordonnees imbriquees) + specs 12/12.


- [x] Referentiels orphelins couverts en lecture seule : payeurs (+details), agences, caisses,
  transporteurs, categories, forfaits (requetes SQL reprises telles quelles du legacy + cache

ef.* existant), etats-patients (liste statique identique). Articles et generateurs via
langages publies (Stock, GMAO). Nouveaux endpoints GET /api/v1/organisation/referentiels/*,
ReferentielsCommunsServiceTest 8/8, SDK frontend + specs.

- [ ] Restent sans equivalent : admin generique (CRUD + import/export + templates) et outillage
  de migration (ReferentialAdmin*, Migration*) : decision ADR requise (porter vers le
  contexte, re-heberger en plateforme comme code technique, ou retirer avec bc-migration).

