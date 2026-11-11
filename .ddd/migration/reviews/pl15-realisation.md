status: done
date: 2026-11-11
sources: pl15-decommissionnement-shared-kernel.md, ADR 0010, 0135, 0136

# PL15 - Réalisation (terminée, aucun commit)

## Sous-étapes
| Étape | Résultat |
|---|---|
| ADR 0136 | écrit (accepted) |
| PL15a1 | supprimés (git rm) : `Money`, `Quantite`, `Poids`, `TransactionRunner`, `SeanceFactureeNonModifiableException`, `MoneyTest`, `QuantiteTest`, `PoidsTest`, `SpringTransactionRunner` ; handler 422 orphelin retiré de `ApiExceptionHandler` (le code `SEANCE_FACTUREE_NON_MODIFIABLE` reste produit par les advices du contexte `seance`). |
| PL15a2 | `TenantScope` -> `infrastructure/security` ; `Email`, `PhoneNumber` (+ `EmailTest`, `PhoneNumberTest`, déplacés) -> `infrastructure/migration/service`. |
| PL15b | `PagedResult` -> `contexts.sharedkernel.domain.PagedResult` (swap d'import, 26 main + 6 test), JSON identique. |
| PL15c/d | `BusinessException` -> `com.hemodialyse.backend.application.erreur` (27 main + 17 test). |
| PL15e/f | `CenterId` -> `CentreId` : `CenterId.of(x)` -> `new CentreId(x)`, `.value()` -> `.valeur()`, dans platform main (dont `CahierDialyseRestController`, `DocumentRestController`, `PatientStatsRestController`, `AssureMigrator`, qui appellent `requireCenter(..).value()`) et test (13 fichiers). |
| PL15g | retrait du module : `backend/pom.xml` (module + dependencyManagement), `platform/pom.xml`, `Dockerfile` l.13, `.ddd/arch-rules.json` (legacy + exclude), `backend/shared-kernel` supprimé (git rm). Règle ArchUnit `plus_aucune_classe_ne_depend_de_l_ancien_noyau_partage` ajoutée à `FrontieresDesContextesTest`. |
| Nettoyage | `backend/db/migration-legacy` (39 fichiers) supprimé ; 7 scripts `pit-*.sh` périmés (références à `bc-*`) supprimés : `pit-a1-consultation`, `pit-a2-cycle-de-vie`, `pit-a3-detection`, `pit-c3-ecritures`, `pit-f3-validation`, `pit-f4-reglements`, `pit-p3-roulement` ; `patient-couverture-pg16-rejeu.sh` retiré (vérifiait 5 entités JPA patient/assure/PEC supprimées en S6c/PL1 et le "module gelé" ; remplacé de fait par les scripts `patient-couverture-pc*-pg16-rejeu.sh`) ; `docs/FLYWAY.md` et `.github/workflows/flyway.yml` mis à jour (plus de tolérance d'archivage vers migration-legacy). Les `pit-*-module.sh` valides sont conservés. |
| covered.json | 4 entrées retirées (fichiers supprimés : SeanceFacturee*, Email, PhoneNumber, Poids du module) ; aucune ajoutée par l'agent. |

## Goldens : aucun ne change
Les 9 `*.approved.json` contenant "CenterId cannot be null" (dm1, pc1 x3, pc3 x3, pc4 x2) restent identiques : ce texte est un littéral des contextes (`SaisieInvalide("CenterId cannot be null")` dans `FicheDemande`, `PatientsControleur`, `AttestationsControleur`...), pas le message du constructeur de `CenterId`. Aucun test ni golden ne dépendait du message de `CenterId`. Aucune archive `_ecarts-legacy`, aucun KB, aucun `.received` à approuver. Les messages littéraux des contextes ne sont pas touchés (hors périmètre de la fusion).

## Vérifications
- Ciblées e/f (migration, référentiel, ACL patientcouverture, Greffe, Cahier, Notification, CenterAccessGuard, Frontieres, Table*, Document, Stats, goldens) : 3 passes (1 ordre par défaut, 2 `-Dsurefire.runOrder=random`) : 453 tests, 0 échec chacune.
- `./mvnw -o test -fae` (backend complet, ordre par défaut) : BUILD SUCCESS, platform 3382 tests, 0 échec, 4 ignorés.
- `check-arch.ts` : 0 violation.
- Un run complet de platform en ordre aléatoire (avant PL15g) a échoué sur 15 classes (AuditWriterServiceTest, IdMappingEtatOrigineMaximal, Lancement...Optimisations (P8), ControleDesPeremptions, pilotagedirection pd1-pd6, SocieteIntegration, InitialSetup, *MigrationTest...) avec cause racine : échec du script `db/seed.sql` (instruction n°266, `autres_traitements_prescription`) à l'initialisation du contexte, indépendant de PL15. Ces 15 classes passent à 79 tests / 0 échec en ordre par défaut. Flakies connus (P8, ControleDesPeremptions) reconnus ; le reste de la liste (dont les pilotagedirection) est signalé sans correction.

## Notes
- Écart au protocole "compilation verte entre sous-étapes" : c/d et e/f ne compilent pas à mi-chemin (deux types distincts) ; compilation et tests verts à la fin de chaque groupe.
- Fichiers non couverts rencontrés : 36 + 4 nouveaux chemins + `CahierDialyseRestController` (inscrits par le coordinateur) ; aucun autre blocage.
