---
slice: organisation-o5-retrait-bc-referential
status: done
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

## Décisions de cutover (2026-11-01, en cours d'exécution)

- **URLs stables** : le nouveau `SocietesControleur` sert à la fois `/api/v1/organisation/societes`
  (canonique) et `/api/v1/societes` (legacy) avec les mêmes DTOs/champs (`SocieteReponse` :
  `hasLogo`, `createdAt`, `centres[]`, `piedDePage`…) — le frontend existant (`societe-api.service.ts`) fonctionne sans
  changement. `ReferentielsLegacyControleur`
  fait de même pour `/api/v1/referentials/*` (forme `RefItem` historique, `GUERRI` inclus,
  filtre `salleId` sur les générateurs).
- **Contrat d'erreur legacy reproduit** : `@ExceptionHandler(IllegalArgumentException)`
  local au contrôleur — codes métier → 422 `{code, detail}` avec les messages français
  historiques (`SOCIETE_DERNIER_CENTRE` → « …son dernier centre »), références inconnues → 404.
  Les `BusinessException` legacy mappaient en 422 via le handler global ; le nouveau code
  lève `IllegalArgumentException("CODE")`, d'où la nécessité de ce mapping (prioritaire sur
  le handler global qui mettrait 400).
- **Relocation de l'admin générique ANNULÉE** : le code `referential/admin` est consommé par
  `bc-migration` (module amont de `platform`) ; le déplacer dans `platform` casse la
  compilation (dépendance circulaire). Il reste dans `bc-referential` jusqu'à la requalification
  de la migration de données (écart n° 4).
- **Preuve de cutover** : `SocieteIntegrationTest` (legacy, platform) doit passer sur la
  nouvelle stack avant suppression des adapters/DTOs/`DomainServiceConfig` et du module.

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

## Progression parite (2026-11-01) — cutover exécuté

- [x] Consulter une societe (GET /api/v1/organisation/societes/{id}) et lister tous les
  centres d'une societe (GET /api/v1/organisation/centres?societeId=) : ajoutes (use cases
  consulterSociete/listerCentres, tests OrganisationServiceTest verts,
  SDK frontend OrganisationApiService + specs vitest a jour, check-arch 0 violation).
- [x] Activer/desactiver (societe et centre) : use cases dedies (activer/desactiverCentre,
  flag actif societe) ; garde dernier-centre-actif (RG-ORG-002/004/013).
- [x] Transfert de centre : use case dedie + gardes meme-societe / cible inactive.
- [x] Logos societe : portes (upload/download/suppression/idsWithLogo, 422 `violations`,
  headers securite) — preuve `SocieteIntegrationTest` 12/12 verte sur la nouvelle stack.
- [x] Liste societes paginee + recherche (RG-ORG-008, cap 100).
- [x] Referentiels orphelins en lecture seule (`ReferentielsCommunsRepository`,
  centres-payeurs/agences/caisses/transporteurs/categories/forfaits/etats) + facade legacy
  `/api/v1/referentials/*` (forme `RefItem`, `GUERRI`, filtre `salleId`) ; articles/generateurs
  via langages publies (catalogue stock, GMAO).
- [x] Stack societe legacy supprimee : `SocieteRestController`, `ReferentialRestController`,
  `SocieteRequests`, `SocieteResponse`, `SocieteRepositoryAdapter`, `ReferentialRepositoryAdapter`,
  beans `societeDomainService`/`referentialDomainService` (DomainServiceConfig), packages
  `domain/organisation` + `domain/referential` + tests.
- [x] Stack admin (`referential/admin`) + migration (`domain/migration`) relocalisees en
  `platform/.../infrastructure/{referential/admin,migration}` (outillage technique, pas du
  domaine) : fonctionnalites `/api/v1/admin/referentials` et `/api/v1/admin/migration`
  preservees (frontend inchange).
- [x] Modules `bc-referential` + `bc-migration` retires du build (pom racine, platform pom,
  Dockerfile) ; caches `ref.medecins/salles/positions/generateurs` rebranches sur les
  nouvelles lectures (memes noms, eviction inchangee) ; 4 tests migres vers le nouveau stack.
- [x] Regle transverse appliquee : plus de records dans les controleurs (DTO dans
  `interfaces/http/dto/`, organisation en reference) ; erreurs centralisees dans un advice
  par contexte (`ErreursDeLOrganisationAdvice`, `ErreursDeLaGmaoAdvice`, + advices existants
  absences/compta/facturation/planification/stock ; 4 contextes sans rien a centraliser,
  documente par les sous-agents).
- [ ] Reste : `ReferentialAdminTables`, entites JPA `SocieteJpaEntity`/`CenterJpaEntity`
  (utilisees par auth/licence : conservees, hors perimetre O5).
- [ ] Cache `ref.*` : cles incluant `centerId`, TTL `CACHE_TTL_REFERENTIALS` ; invalider
  a la bascule.
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


- [x] Invariants metier portes : unicite des codes societe/centre (SOCIETE_CODE_DEJA_UTILISE,
  CENTRE_CODE_DEJA_UTILISE), interdiction de desactiver/transferer le dernier centre actif
  (SOCIETE_DERNIER_CENTRE_ACTIF, RG-ORG-013), changement de code centre autorise.
- [x] Correction bug bloquant : CentresJdbc lisait des colonnes inexistantes (useau,
  capacite) et rouver/listerParSociete ignoraient useau_horaire/tous les centres ;
  capaciteTheorique supprime (aucune colonne, aucun consommateur legacy).

