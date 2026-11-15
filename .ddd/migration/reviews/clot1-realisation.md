---
status: proposed
date: 2026-11-15
sources: revue globale `.ddd/reviews/2026-11-15-global-decommissionnement-platform.md` ; ADR 0203-0206
---
# CLOT1 : réalisation (refactoring sur place, ADR 0024)

## Livré
| Action | Résultat |
|---|---|
| A (M3) | `InvalidationDeLaValorisationDuStock` (pilotage-direction, `infrastructure/acl/stock`, bean dans `PilotageDirectionConfiguration`) vide `stock.valorisation.articles` sur `MouvementsDeStockModifies` et `VerrousDeRecalculModifies` (hors `STARTED`). `StockEvenementsListener` ne fait plus que relayer vers STOMP. `RelaiDesInventairesVersWebSocket` et `WebSocketDirectionPublisher` restent (noyau de transport, ADR 0203) : topics et payloads inchangés. Test unitaire ajouté (2 tests). |
| B (M1) | ADR 0204 : migrators de reprise = règles de normalisation de l'import, noyau de l'outil de reprise ; retrait à l'événement « fin de la reprise de production » ; décision déléguée, à confirmer métiers/DPO <= 2026-12-15. Grep préalable : aucun JDBC/SQL/table de contexte dans `infrastructure/migration/service/*` (seuls ports publiés `RepriseDes*` et `shared-kernel`). |
| C (M2) | ADR 0205 : Flyway centralisé V1-V59 (une base, un historique). ADR 0014 amendé en en-tête (aucun `CREATE SCHEMA` n'a jamais existé). Aucun script Flyway modifié. |
| D (M4) | `context-map.md` : tableau Strangler mis à jour (13 contextes extraits, état de platform, 10 ACL, relations R-DOC5, R-STK-PIL, R-ORG-AVAL, R-REPRISE). Glossaire Documents : `Jeu de données`, `LIGNES`, `Requête modifiée (QUERY_MODIFIED)` (proposed). ADR 0206 : dépendance pom organisation -> stock/gmao justifiée (Conformist, langage publié seulement ; stock/gmao ne dépendent pas d'organisation). |
| E | Butoir mort supprimé de `TableDeLOrganisationTest` (constante datée, test de butoir) ; garde permanente conservée. Constantes mortes 2027 supprimées dans `TableDesAnalysesTest`, `TableDesSeancesTest`, `TableDuDossierMedicalTest` ; branche « revue/butoir 2027 » supprimée de `TableDuPatientCouvertureTest.chaque_jalon_porte_une_date_et_une_echeance`. Dossiers vides supprimés : `patientcouverture/infrastructure/acl/legacygmao`, `src/test/resources/characterization/gmao/plans` (les `acl/{absences,dossiermedical,seance,pilotagedirection}` de platform n'existaient déjà plus sur disque). |
| F (M5) | `.ddd/migration/verifications-production.md` § 88 : requêtes SELECT (modèles avec `<queryString`, collation, codes sans tirets) + critère de décision. Aucun code touché. |
| G | Aucun test déplacé (voir ci-dessous). |

## Butoirs restants (à décider, non touchés)
- `ButoirsDesGardesDeTables` (date réelle) et la mécanique « revue 2026-12-15 / butoir propre » des `Lecteur` de `TableDuDossierMedicalTest`, `TableDesSeancesTest`, `TableDesAnalysesTest` : actives sur des listes VIDES (aucun butoir vivant). Elles imposeraient un butoir à tout futur lecteur ; à remplacer par « liste vide, garde permanente » dans une slice dédiée si la directive « aucun butoir daté » doit s'appliquer aussi aux futurs lecteurs.
- Les « revue 2026-12-15 » des KB sont une séance de confirmation, pas un butoir de code.

## G : tests non-HTTP restés en platform (aucun déplacé)
Les modules de contexte n'ont pas d'application Spring Boot de test (`*TestApplication` sans balayage ni auto-configuration, pas de `schema.sql`/seed d'identité ni de centre de démonstration `1111...`). Déplacer un `@SpringBootTest` exigerait une dépendance de test sur platform (interdite) ou la duplication du seed/schéma.
| Test | Raison |
|---|---|
| ComptabiliteStockIntegrationTest | `UserPrincipal` (platform) + assemblage comptabilité/stock/facturation |
| ValorisationArticlesCacheIntegrationTest | prouve de bout en bout l'invalidation déplacée (Stock publie -> Pilotage vide) : inter-contextes |
| NotificationJournalIntegrationTest | contexte complet (schéma + sécurité platform) |
| EffectifIntegrationTest, PredicatDEffectifDuPilotageGardeIntegrationTest | parité Pilotage x Patient et couverture : inter-contextes |
| DirectionCapaciteIntegrationTest, IndicateursDeLaDirectionIntegrationTest | Pilotage x GMAO x Planification, seed de platform |
| JeuDeDemonstrationDuStockIntegrationTest | centre de démonstration du seed de platform, partagé |
| AutorisationsDeLOrganisationIntegrationTest | `RoleScopeFilter`, `UserPrincipal` (platform) |
| Forfaits/PayeursParPatientCouvertureTest, HistoryMigrationTest | testent des classes de platform (ACL conservées, migrators) |
| *MigrationTest de tables (ComptabiliteStock, GmaoTables, IndexPerformance, PlanComptable) | valident la chaîne Flyway centralisée (ADR 0205) : propriété de platform |
Condition de déplacement : événement « un contexte reçoit son application de test avec schéma », pas une date.

## Fichiers legacy / guard-legacy
Aucun blocage. `covered.json` non modifié ; lignes à retirer/ajouter : aucune connue (fichier platform modifié : `StockEvenementsListener`, édité sans blocage).

## Vérifications
Voir le rapport de l'appelant (passes ciblées et build complet) : section « Résultats » ci-dessous.

## Résultats
- Trois passes ciblées (normale, `-Dsurefire.runOrder=random`, normale ; modules pilotage-direction + platform avec `-am`, `Table*Test`, `FrontieresDesContextesTest`, tous les `ArchitectureHexagonaleTest`, `ValorisationArticlesCacheIntegrationTest`, caractérisations stock) : BUILD SUCCESS ×3, 242 tests en passe 1 dont les `TableDe*Test` (TableDeLOrganisationTest : 6) et ArchUnit ; `InvalidationDeLaValorisationDuStockTest` 2/2.
- Build complet (`clot1-full.log`) : `Tests run: 3589, Failures: 1, Errors: 0, Skipped: 4`. Seul échec : `InventairePl7CharacterizationIntegrationTest` (14 tests, 1 échec : `feuille_de_comptage`, échec connu). `LancementEtSuiviDesOptimisationsApplicationTest` : traces d'erreurs attendues dans le log, aucun échec compté.
- `check-arch.ts` : 0 violation.
