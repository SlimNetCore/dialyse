---
slice: seance-se7e-lectures-pilotage-et-rapports
status: characterized (audit vérifié sur le code et les tests réels, filet complété le 2026-11-08 ; aucun code de production modifié)
date: 2026-11-08
context: seance (consommateurs : pilotage de la Direction, statistiques du patient, cahier de dialyse, reprise de données, référentiel)
sources: [.ddd/migration/slices/seance-se7e-lectures-pilotage-et-rapports.md, TableDesSeancesTest (allow-list, 12 lecteurs), filets F5 (LecturesFinancieresDirection…), A1 (LecturesDeAbsencesParLaDirection…), DM1, DM8, CahierDialyseReportIntegrationTest, MigrationHistoryIntegrationTest, DirectionIntegrationTest, DirectionBreakdownIntegrationTest]
---

# SE7e : audit et filet des derniers lecteurs SQL de `seances` côté plateforme

Chemins relatifs à `backend/platform/src/main/`. Légende : **couvert** = un test échoue si la requête change (prédicats, colonnes, tri, centre) ; **partiel** = cas nominal seulement ; **non couvert**. « Avant » = filet existant relu dans le code des tests ; « après » = avec SE7e.

## 0. Périmètre : 12 entrées de l'allow-list, 2 lecteurs cachés

| Fichier | Lot | Raison |
|---|---|---|
| `java/…/application/direction/DirectionDashboardQueryService.java`, `DirectionBreakdownQueryService.java`, `DirectionAbsencesQueryService.java` | SE7e | pilotage de la Direction |
| `java/…/application/query/PatientStatsQueryService.java` | SE7e | statistiques du patient |
| `java/…/infrastructure/reporting/CahierDialyseReportService.java`, `resources/reports/cahier_dialyse.jrxml` | SE7e | rapport imprimé |
| `java/…/persistence/adapter/migration/MigrationRollbackJdbcAdapter.java` | SE7e | reprise de données |
| `java/…/persistence/adapter/migration/HistoricalRecordJdbcAdapter.java` | SE7e, **hors allow-list** | lit ET écrit `seances` par nom de table calculé (`TABLES.put(SEANCES, new Table("seances", …))` `:50`), invisible à la garde (KB-SEA-92) |
| `java/…/persistence/adapter/referential/ReferentialAdminJdbcAdapter.java` + `ReferentialAdminTables.java:23` | SE7e, **hors allow-list** | `SELECT COUNT(*) FROM " + usage.table()` (`:201`), usage `seances.forfait_override_id` (KB-SEA-92) |
| `java/…/infrastructure/acl/seance/HistoriqueFourniLegacy.java`, `ForfaitsFournisLegacy.java` | **SE7f** | fournisseurs du langage publié `seance.v1` : ils migrent dans `contexts/seance` avec le retrait de bc-seance (fiche SE7f) ; déjà couverts par les contrats `LecturesDeLaSeanceContrat` (glob `acl/seance/*.java` de `covered.json`) |
| `resources/db/seed.sql`, `db/demo/renadial-seed.sql`, `db/demo/renadial-purge.sql` | **SE7f** | scripts de DONNÉES (amorçage rejoué à chaque démarrage hors profil `prod`, `application.yml:22-26` ; démonstration ; purge) : aucun comportement métier observable à figer, 19 000 séances synthétiques ; SE7f décide de leur statut (fixtures listées ou déplacées avec la table) |

Correction de la fiche de slice : `MigrationRollbackJdbcAdapter` n'est pas « une occurrence » mais 3 requêtes (dont 2 par nom calculé) ; `HistoricalRecordJdbcAdapter` et `ReferentialAdminTables` n'ont aucune occurrence littérale (d'où leur absence de l'allow-list) ; « Pilotage PD1 à PD5 » n'existe pas comme filet (seule mention : `LectureSeuleDuPilotageTest`, garde d'architecture) : le filet réel du pilotage est F5 (`characterization/facturation/pilotage`) et les tests d'intégration `Direction*IntegrationTest`.

**Scripts Flyway** (`resources/db/migration`, exclus de la garde) : `V1` (création), `V21`, `V23`, `V26`, `V39` (ajouts de colonnes), `V31` (index). Aucun ne lit ni n'écrit de données de `seances` (aucun `SELECT/INSERT/UPDATE/DELETE` sur la table, vérifié par recherche). Migrations versionnées, protégées par la somme de contrôle Flyway : figées une fois appliquées, hors périmètre. L'état d'application en production n'est pas vérifiable depuis le poste (voir questions ouvertes).

## 1. Requêtes, appelants, tests

| # | Requête (`chemin:ligne`) | Colonnes / prédicats / tri / centre / NULL / agrégats | Appelant observable | Tests existants qui l'observent réellement | Avant | Après |
|---|---|---|---|---|---|---|
| E1 | `DirectionDashboardQueryService.java:121` | `center_id, COUNT(*)` ; `center_id IN (centres de la société)`, `date_seance BETWEEN` (inclus), `statut IN ('VALIDEE','SIGNEE','FACTUREE')` (casse exacte) ; `GROUP BY center_id` ; AUCUNE jointure `patients` | `GET /api/v1/direction/overview` (DIRECTION, société du jeton revérifiée) ; `GET /direction/report` (PDF) ; `POST /direction/snapshots/{mois}` (figé) ; `DirectionRealtimeService:156` (période par défaut) | F5 `overview_*` (FACTUREE, SIGNEE, VALIDEE, PLANIFIEE, centre B, société 2) ; `DirectionIntegrationTest` (3 VALIDEE, date du jour) | partiel (bornes, casse, ANNULEE/ABSENT, doublon, fiche ailleurs) | couvert (KB-SEA-83) |
| E2 | même fichier `:167` (période précédente) | `COUNT(*)` même prédicat ; période de même durée juste avant (`start-1` en arrière de `days-1`) | idem | F5 `overview_janvier_fevrier_2026`, `overview_un_jour` | partiel (bornes) | couvert |
| E3 | même fichier `:183` | `EXTRACT(YEAR/MONTH)`, `COUNT(*)` ; `GROUP BY center_id, y, m` ; clé triée `yyyy-MM|centre` en Java | idem (`mensuel`) | F5 (un ou deux mois) ; `DirectionIntegrationTest` (`size() >= 1`) | partiel (année, jour unique) | couvert (H2 + PG16 E3) |
| E4 | `DirectionBreakdownQueryService.java:190` | `s.center_id, COALESCE(ca.code,'')`, `MAX(ca.nom)`, `COUNT(*)` ; `INNER JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id` + payeur/agence/caisse SANS centre ; même prédicat | `GET /direction/breakdown`, rapport PDF, instantané | `DirectionBreakdownIntegrationTest` (CREE ignorée, autre centre ignoré, date du jour) ; F5 `breakdown_caisses_*` | partiel | couvert (KB-SEA-83, 84) |
| E5 | `DirectionAbsencesQueryService.java:115` | comme E1 ; dénominateur de `tauxAbsenteisme` (2 décimales, HALF_UP), non masqué | `GET /direction/absences` | F5 `absences_part_du_ca_*` ; A1 `LecturesDeAbsencesParLaDirection…` (12 séances de mars, CREE, ABSENT) | partiel | couvert |
| E6 | `PatientStatsQueryService.java:294-297` (`baseFrom`, compte) | `COUNT(1)` ; `seances s LEFT JOIN volet_paramedical vp ON vp.seance_id = s.id` (SANS centre du volet) ; `s.center_id = ? AND s.patient_id = ?` ; dates en LITTÉRAL (`:270-275`) ; AUCUN statut | `GET /api/v1/patients/{id}/stats/paramedical`, `POST /patients/stats/paramedical/search` (ADMIN, INFIRMIER, MEDECIN, SECRETAIRE) ; `GET /patients/{id}/stats/export?format=pdf`, `POST /stats/export/search` (ADMIN) | DM1 `autres_lecteurs` (dates relatives) ; DM8 `pdf` (2 séances TERMINEE) | partiel | couvert (KB-SEA-85) |
| E7 | même fichier `:298-300` | `AVG` de NUMERIC ; nul rendu `0.0` (`:358-361`) | idem | DM1 | partiel | couvert H2 + PG16 (KB-SEA-87) |
| E8 | même fichier `:302` + `:39-43` | `date_seance`, poids, poids sec en vigueur (`LIMIT 1`, `date_prescription DESC, updated_at DESC`), UF (`COALESCE(uf, (avant-après)*1000)`) ; `ORDER BY s.date_seance` SEUL | idem (`poidsEvolution`, tableau du PDF) | DM1, DM8 | partiel (égalité de date, prescriptions du même jour, centre) | couvert ; ordre intra-jour NON verrouillé (KB-SEA-86) |
| E9 | même fichier `:311` | `date_seance, ta_avant, ta_apres` → tension découpée en Java | idem (`taEvolution`) | DM1 | partiel | couvert |
| E10 | `CahierDialyseReportService.java:84` | `COUNT(*)` ; centre, patient, 3 statuts (casse exacte), `BETWEEN` (défaut 1900-01-01 / 2999-12-31) ; 0 → 422 `CAHIER_VIDE` | `GET /api/v1/cahier-dialyse/{id}/imprimer` (ADMIN, SECRETAIRE, MEDECIN, INFIRMIER ; centre de la session) | `CahierDialyseReportIntegrationTest` (service : vide, période invalide, patient hors centre) | partiel (casse, bornes, HTTP) | couvert |
| E11 | `reports/cahier_dialyse.jrxml:42-135` | une ligne par séance × consommable ; jointures volets, dossier, forfait (surcharge sinon PEC), prescription en vigueur, sorties de stock, toutes AU CENTRE ; `ORDER BY s.date_seance DESC, s.id, ac.code` | idem (PDF ; EXCEL ou HTML selon le modèle de document du centre) | `CahierDialyseReportIntegrationTest` (Jasper rempli directement : contenu complet, prescription en vigueur, autre centre) | partiel (pas d'HTTP, ni égalité de date, ni surcharge) | couvert pour le PDF (KB-SEA-88) ; EXCEL/HTML non rejoués |
| E12 | `HistoricalRecordJdbcAdapter.java:94` (`findExisting`, séances) | `SELECT id FROM seances WHERE center_id = ? AND date_seance = ? AND patient_id = ? ORDER BY created_at` (clé `HistoryRules.java:301`), SANS statut | `POST /api/v1/admin/migration/batches/{id}/import/seances` (ADMIN, SUPERADMIN) | `MigrationHistoryIntegrationTest` (rejeu sans doublon) ; `HistoryMigrationTest` (ports en mémoire) | partiel | couvert (KB-SEA-89) |
| E13 | même fichier `:118` (`insert`) | `INSERT INTO seances (id, center_id, patient_id, date_seance, statut, created_at)` ; statut `FACTUREE` ou `SIGNEE` (`HistoryRules.java:318`) ; `update` (`:140`) inatteignable pour les séances (`updatable() = false`, `:308`) | idem | `MigrationHistoryIntegrationTest` | couvert | couvert |
| E14 | `MigrationRollbackJdbcAdapter.java:83` (`PATIENT_DATA`, `:37`) | `SELECT CAST(id AS VARCHAR(36)) FROM seances WHERE patient_id = ?` SANS centre ; table calculée | `POST /batches/{id}/cancel` (ADMIN, SUPERADMIN) : 422 `MIGRATION_CANCEL_BLOCKED` | `MigrationHistoryIntegrationTest.importsTheWholeHistoryThenCancelRemovesIt` (nominal) | partiel | couvert (KB-SEA-90) |
| E15 | même fichier `:106` + volets `:108-109` | `facture_id IS NOT NULL` par `id` (sans centre) ; volet médical ou paramédical = « complétée » ; facturée prime | idem | `cancelIsRefusedOnceAMigratedSessionIsBilledInThePlatform` (facture seule) | partiel | couvert |
| E16 | même fichier `:143` | `DELETE FROM seances WHERE id = ? AND center_id = ?` (table calculée) | idem | nominal | couvert | couvert |
| E17 | `ReferentialAdminJdbcAdapter.java:201` (usage `ReferentialAdminTables.java:23`) | `SELECT COUNT(*) FROM seances WHERE forfait_override_id = ?` SANS centre ni statut | `DELETE /api/v1/admin/referentials/forfaits/{id}` (ADMIN, SUPERADMIN) : 422 `REFERENTIAL_IN_USE` (nombre de références) | `ReferentialAdminIntegrationTest` (caisse/agence seulement) | non couvert | couvert (KB-SEA-91) |

## 2. Filet ajouté (points d'observation)

`backend/platform/src/test/java/com/hemodialyse/backend/characterization/seance/se7e/` : harnais `Se7eCharacterizationSupport` (au-dessus de SE7d : MockMvc, Spring complet, sécurité réelle + `RoleScopeFilter`, filtre d'écart de 2 ms ; société SE7e = centres A et B, autre société = centre C, compte DIRECTION revérifié en base ; centres préexistants rétablis à l'identique ; dates FIXES `<D±n>`, aucun `now`, aucune zone système ; PDF décodés par PDFBox, horodatages d'édition masqués `<EDITE_LE>`, `<GENERE_LE>`) et 5 classes, 15 tests, 15 goldens `…/resources/characterization/seance/se7e/{pilotage,statistiques,cahier,reprise,referentiel}/`, approuvés un par un (lecture du `.received`, écriture de l'`.approved.json`).

| Classe | Tests | Point d'observation | Requêtes |
|---|---|---|---|
| `LesSeancesLuesParLePilotageDeLaDirectionCharacterizationIntegrationTest` | vue d'ensemble (bornes, lendemain, veille, casse, blanc, 5 statuts non réalisés, doublon, fiche au centre B, autre société) ; année traversée et jour unique ; répartition par caisse vs vue d'ensemble ; taux d'absentéisme (centre masqué) ; rapport PDF | HTTP DIRECTION (JSON, PDF en texte) | E1-E5 |
| `LesSeancesLuesParLesStatistiquesDuPatientCharacterizationIntegrationTest` | tous statuts et volet d'un autre centre ; 6 périodes (bornes, from seul, to seul, jour unique, vide, moyennes périodiques) ; export PDF | HTTP (INFIRMIER, ADMIN) | E6-E9 |
| `LeCahierDeDialyseImprimeCharacterizationIntegrationTest` | statuts, bornes, égalité de date (identifiant croissant), centre B ; refus (`CAHIER_VIDE`, `PATIENT_INTROUVABLE`, `CAHIER_PERIODE_INVALIDE`) ; surcharge de forfait avec et sans code | HTTP INFIRMIER, PDF en texte | E10-E11 |
| `LesSeancesDeLaRepriseDeDonneesCharacterizationIntegrationTest` | séance déjà présente (ANNULEE au centre, VALIDEE au centre B) et annulation bloquée ; complétée / facturée ; séance du patient repris dans un autre centre puis annulation | HTTP ADMIN (multipart) + état SQL de `seances` | E12-E16 |
| `LesSeancesQuiUtilisentUnForfaitDuReferentielCharacterizationIntegrationTest` | surcharge VALIDEE, au centre B, sur deux ANNULEE, sans usage | HTTP ADMIN + présence du forfait | E17 |

PostgreSQL 16 : `backend/scripts/seance-se7e-pg16-rejeu.sh` (cluster jetable, port 55549 par défaut, 5432 refusé, mot de passe `SEANCE_PG16_PASSWORD`, requête du jrxml passée par fichier UTF-8) rejoue à l'identique, en SELECT, les requêtes E1-E12, E14, E15 et E17 (jrxml ENTIER pour E11) sur la chaîne Flyway : 19 vérifications, code 0 (libellés du script = numéros de l'audit ci-dessus, corrigés le 2026-11-08, revue SE7e m2). Les écritures E13 (INSERT), `update` et E16 (DELETE) ne sont PAS rejouées par le script (ses écritures sur `seances` ne sont que le jeu de données) ; elles sont vérifiées sur PostgreSQL par le contrat `PilotageEtRapportsDesSeancesPostgresContractIT` (liste blanche, centre du lot, suppression), lancé en fin de script depuis `4cd1ab8a` ; au moment du filet (`b46f81dd`) elles n'étaient couvertes que par `MigrationHistoryIntegrationTest` sur H2. Confirmé : mêmes comptes et mêmes jointures que H2 ; `AVG` = `74.1666666666666667` (H2 : `74.166666666667`, KB-SEA-87) ; libellés de colonnes en minuscules (H2 : majuscules, KB-SEA-86) ; `EXTRACT` numérique lu par `getInt` ; ordre du cahier à date égale par identifiant croissant identique ; `FETCH FIRST` et `CAST` du jrxml acceptés.

## 3. Seams
Aucun demandé, aucun appliqué (`seamAllowed` inchangé).

## 4. Bugs constatés (non corrigés)
KB-SEA-83 à KB-SEA-92 dans `known-bugs.md` (revue au plus tard le 2026-12-15).

## 5. Limites (ce qui n'est PAS verrouillé)
- Ordre de deux séances du même jour dans les statistiques (E8, E9) et ordre des caisses à chiffre d'affaires égal (E4) : non déterministes par construction ; les goldens trient (KB-SEA-84, 86). Ordre des bloquants d'annulation : suit `migration_id_map ORDER BY created_at` sans départage, trié par le test.
- Clés JSON de `poidsEvolution` : le golden fige H2 (MAJUSCULES) ; la production (PostgreSQL) sert des minuscules d'après les libellés rejoués (E9) ; la sérialisation JSON elle-même n'est pas rejouée sur PG16 (hypothèse, KB-SEA-86). Idem pour la précision des moyennes (KB-SEA-87).
- Instantanés de la Direction (`POST /direction/snapshots/{mois}`, borné par `YearMonth.now()`), diffusion temps réel (`DirectionRealtimeService`, période par défaut = aujourd'hui) et périodes par défaut (`to` absent = aujourd'hui) : non rejoués (règle « aucune lecture de l'heure ») ; le comptage est le même code (E1-E3).
- Cahier au format EXCEL ou HTML (modèle de document personnalisé du centre) : non rejoué ; seules les lignes de séance du PDF sont figées (consommables, dossier, prescription : `CahierDialyseReportIntegrationTest`).
- Âges de la répartition (`LocalDate.now()`) : non projetés (hors `seances`).
- Couverture de branches et mutation : non mesurées (PIT absent pour ces classes, comme SE7c et SE7d).

## 6. Exécutions
Nouveaux tests : 3 exécutions consécutives vertes (15/15 chacune), la 3e avec `-Dsurefire.runOrder=random` ; aucun `.received` restant. Suite complète `./mvnw -o test -fae` depuis `backend/` (un seul build, aucune relance) : BUILD SUCCESS, 7 683 tests selon les 15 lignes de résumé « Tests run » de Maven, 0 échec, 0 erreur, 10 ignorés. `check-arch` : 0 violation. Rejeu PG16 (port 55549) : 19 vérifications, code 0.

## Questions ouvertes
- Production : `SELECT version, success FROM flyway_schema_history ORDER BY installed_rank` pour confirmer que V1 à V53 (51 fichiers) sont appliqués (aucune ne touche aux données de `seances`).
- ~~Production : la variable `SEED_DATA_LOCATIONS` est-elle positionnée ?~~ Close le 2026-11-08 (revue SE7e m3) : la prémisse était inexacte. `SEED_DATA_LOCATIONS` REMPLACE le script, ne coupe pas le rejeu ; c'est le profil `prod` (`application-prod.yml:15`, `spring.sql.init.mode: never`, posé par `docker-compose.prod.yml:93`) qui coupe tout. Analyse complète et risque résiduel (instance sans profil `prod`) : fiche de slice, « Questions ouvertes », 1er point. Statut des scripts de données : SE7f.
