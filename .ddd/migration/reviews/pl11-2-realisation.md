---
status: proposed (TERMINÉ après reprise : voir ADR 0158 ; seule la section « RESTE À FAIRE » ci-dessous est périmée)
date: 2026-11-13
sources: pl11-analyse.md (4, 6), pl11-decisions.md, pl11-0-filet.md, pl11-1-realisation.md, ADR 0157
---
# PL11-2 : créneaux (position_creneau) dans l'Organisation : ÉTAT EXACT

## Fait (code écrit ; tests Organisation verts : GererLesCreneauxTest 9, OrganisationServiceTest, OrganisationJdbcContratTest)
- Organisation domaine : `Creneau` (+ `actif`, `corriger`, `desactiver`, `reactiver`, `verifierLUnicite`), `SaisieDeCreneau`, `SaisieDeCreneauInvalide`, `AnomalieDeSaisie`, `CreneauDejaExistant` ; `CreneauRepository` étendu (`listerTousParCentre`, `page`, `supprimer`).
- Organisation application : `GererLesCreneaux` (créer/modifier/supprimer-ou-désactiver/lister/importer/proposes).
- Organisation langage publié : `GestionDesCreneaux`, `RefusDeCreneau`, `UsageDuCreneau`, `DescripteurDeReferentiel`, `DescripteursDeLOrganisation.CRENEAU` ; bean `gestionDesCreneaux` ; `CreneauxJdbc` (colonne `actif`, page SQL identique à l'ancienne).
- Usages (D3), un `UsageDuCreneau` par propriétaire (ports publiés par l'Organisation, implémentés côté propriétaire car ils dépendent d'Organisation : un ACL dans Organisation créerait un cycle Maven) : Patient et couverture (`patients.position_id`, `mouvement_patient.position_id`), Séance (`seances.creneau_id`), Planification (`infirmier_affectation`, `infirmier_remplacement`, `planification_calendrier_case`, `deplacement_temporaire`). Erreur => propagée => `RefusDeCreneau` CONTROLE_D_USAGE_IMPOSSIBLE (D10) -> 422 `REFERENTIAL_USAGE_UNVERIFIABLE`.
- D4 : créneau utilisé -> `actif=false` (204) ; non utilisé -> suppression physique. Migration `V58__creneau_desactivation_logique.sql` + `schema.sql`. Recréer/réimporter un code désactivé le réactive (choix de ma part, calqué sur D6, à confirmer).
- Plateforme : ACL `acl/organisation/CreneauxParOrganisation` + `ReferentielsDeLOrganisation` ; `ReferentialKind.CRENEAU` et son entrée `ReferentialAdminTables` retirés ; `DomainServiceConfig` ; `PatientMigrator` et `ReferentialAdminJdbcAdapter.findAllForMatching` utilisent `GestionDesCreneaux#proposes` (en attendant PL11-5).
- Content-Type : `consumes` retiré de POST/PUT, `AutorisationAvantLeCorpsDesReferentiels` (intercepteur MVC) : 403 avant 415.
- covered.json : 3 nouveaux fichiers de plateforme ajoutés. Tests plateforme adaptés : `ReferentialAdminDomainServiceTest` (CRENEAU -> SALLE), `LaFacadeDesReferentielsNeDecritQueLesReferentielsEncoreDirectsTest` (garde anti-retour étendue à CRENEAU).

## MISE À JOUR FINALE (fin de la limite de tours)
- Fait depuis : `ReferentialCatalogueTest` corrigé ; goldens pl11-0-creneaux `refus-d-usage-par-dependance_nouveau_code` et `audit_nouveau_code` relus (écarts = D3/D4 uniquement : 204 au lieu de 422, créneaux désactivés conservés) et archivés (`_ecarts-legacy/pl11-0-creneaux/*_avant_pl11-2.legacy.json`) ; 6 autres goldens créneaux, pl11-0-transverse et rf3 `creations` identiques.
- Intercepteur : 415 pour un rôle autorisé sans type JSON, 403 d'abord pour un rôle non autorisé. OUVERT : le détail du 403 diffère selon le chemin (`Accès refusé` dans refus-de-forme, `Access Denied` dans `rf3/saisie-caisses/creations-gardees` « secretaire » avec corps JSON). Avec le message « Accès refusé », `refus-de-forme` redevient identique à l'archive `_avant_pl11-1` pour les lignes secrétaire (à restaurer) mais `creations-gardees` « secretaire » passe de `Access Denied` à `Accès refusé` : à trancher (le message de l'exception de l'intercepteur doit varier selon le cas, ou intercepter seulement quand le type de contenu est absent/illisible). Les 2 goldens rf3 `refus-de-forme` et `creations-gardees` ÉCHOUENT donc encore ; leurs `.received` ont été supprimés, approved inchangés.
- Non lancé : 3 passes ciblées, build complet, check-arch, règle ArchUnit anti-retour, docs/ADR 0158.

## RESTE À FAIRE (non fait)
1. Compiler/tester la plateforme : `ReferentialCatalogueTest` (attend probablement `creneaux` dans les directs), `ReferentialAdminIntegrationTest`, `ReferentialAdminDelegationTest`, `InMemoryReferentialAdminRepository` peuvent devoir suivre.
2. Goldens pl11-0-creneaux à régénérer depuis `.received` et relire UN PAR UN : `refus-d-usage-par-dependance` (01 deux patients 422 -> 204 désactivé ; 03-06 : 204 inchangé mais créneau désactivé ; liste finale), `audit_nouveau_code` (suppression refusée -> 204), le SQL `ETAT` ne lit pas `actif` (les désactivés y apparaissent : à adapter si besoin). Archiver `_ecarts-legacy/pl11-0-creneaux/*_avant_pl11-2.legacy.json`. Les 3 goldens rf3 (agences, caisses, centres payeurs « sans Content-Type » 415 -> 403) : régénérer, comparer aux `_avant_pl11-1` archivés, restaurer proprement si identiques. Autres goldens inchangés (à vérifier).
3. Ajouter un golden de l'erreur du contrôle d'usage des créneaux (optionnel) ; test de contrat des `UsageDuCreneau*` (JDBC) ; exclure les créneaux désactivés de `SallesEtCreneauxDuCentreJdbc` (planification) ? non fait.
4. Règle ArchUnit anti-retour côté Organisation (aucun SQL `position_creneau` hors Organisation par la plateforme) ; `check-arch.ts` ; 3 passes ciblées (dont `-Dsurefire.runOrder=random`) ; build complet `-o test -fae`.
5. Butoirs 2027-03-31 : AUCUNE entrée soldée par PL11-2 d'après pl11-analyse.md §6 (`ReferentialAdminJdbcAdapter`/`ReferentialAdminTables` restent listés pour salle et médecin jusqu'à PL11-4/6) ; rien repoussé.
6. Docs : ADR 0158, KB-PL11-1 (créneau) / KB-PL11-3 « CORRIGÉ (2026-11-13) », KB-PL11-2 reproduit (compte par identifiant seul), référentiel RG-REF, annexe, glossaire Organisation.

## Décisions à signaler
- KB-PL11-2 (compte non borné au centre) : REPRODUIT (le port prend l'identifiant seul), car pl11-decisions.md ne le décide pas.
- D7 non appliqué aux routes `/organisation/creneaux` (hors consigne : seules D3, D4, D10) ; o2 inchangé.
