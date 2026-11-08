---
slice: seance-se7e-lectures-pilotage-et-rapports
status: built
date: 2026-11-08
order: 7.5
context: seance
parent: seance-se7-lectures-publiees
depends_on: [seance-se7b-tableau-de-bord-mensuel]
sources: [ADR 0099, .ddd/safety-net/seance-se7e.md, .ddd/safety-net/known-bugs.md (KB-SEA-83 à 92), .ddd/migration/verifications-production.md §64, .ddd/safety-net/seams.md (SE7e lot C)]
---

# SE7e : lectures de Pilotage direction, statistiques patient, cahier de dialyse, reprise, référentiel

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URL, aucun flag, aucune migration. Décisions : ADR 0099 (accepted, à confirmer métiers / DPO au plus tard le 2026-12-15). Audit des 17 requêtes : `.ddd/safety-net/seance-se7e.md`.

## Avant SE7e (rappel)
| Fichier de plateforme (édité, chemins depuis `backend/platform/src/main/`) | Requêtes | Nature |
|---|---|---|
| `java/…/application/direction/DirectionDashboardQueryService.java` | E1, E2, E3 | vue d'ensemble, période précédente, mois |
| `java/…/application/direction/DirectionBreakdownQueryService.java` | E4 (jointure `patients`, caisse) | répartition par caisse |
| `java/…/application/direction/DirectionAbsencesQueryService.java` | E5 | dénominateur du taux d'absentéisme |
| `java/…/application/query/PatientStatsQueryService.java` | E6 à E9 (volet paramédical, prescription) | statistiques paramédicales |
| `java/…/infrastructure/reporting/CahierDialyseReportService.java` | E10 | refus `CAHIER_VIDE` |
| `resources/reports/cahier_dialyse.jrxml` | E11 | contenu du cahier : **inchangé** (décision 3) |
| `java/…/persistence/adapter/migration/HistoricalRecordJdbcAdapter.java` | E12, E13 (+ `update` inatteignable), nom de table calculé | import des séances reprises (ÉCRITURE) |
| `java/…/persistence/adapter/migration/MigrationRollbackJdbcAdapter.java` | E14, E15, E16 (nom calculé) | annulation d'un lot (SUPPRESSION) |
| `java/…/persistence/adapter/referential/ReferentialAdminJdbcAdapter.java` + `ReferentialAdminTables.java` | E17 (usage indirect) | refus de suppression d'un forfait |

## Lots livrés (commit filet `b46f81dd` ; lots A à D non commités, le coordinateur commite)
| Lot | Contenu |
|---|---|
| Filet | `b46f81dd` : 15 goldens SE7e, rejeu PG16, KB-SEA-83 à 92 ; revérifié vert sur le legacy avant tout code (15/15, 2026-11-08) |
| A | ADR 0099 ; 5 ports publiés `seance.v1` (types JDK) : `SeancesRealiseesDesCentresPubliees`, `StatistiquesDesSeancesPubliees`, `SeancesDuCahierPubliees`, `ForfaitsImposesAuxSeancesPublies`, `SeancesDeLaReprisePubliees` (écritures) |
| B | `contexts/seance/.../persistence/SeancesRealiseesDesCentresJdbc`, `StatistiquesDesSeancesJdbc`, `SeancesDuCahierEtForfaitsImposesJdbc`, `SeancesDeLaRepriseJdbc`, SQL mot pour mot, beans dans `SeanceConfiguration` ; contrat `PilotageEtRapportsDesSeancesContrat` (13 cas) joué par `PilotageEtRapportsDesSeancesEnMemoire`, H2 (`…JdbcContratTest`) et PG16 (`…PostgresContractIT`, rejoué par `seance-se7e-pg16-rejeu.sh`) |
| C | Bascule des 9 fichiers de plateforme (injection directe des ports, aucun cycle Maven, aucun fournisseur) ; seam OUVERT puis FERMÉ, chemins exacts (`seams.md`, `seamAllowed` vide) ; garde durcie (`NomsDeTableCalcules`, `TableDesSeancesTest` 12 vers 6) ; 6 règles ArchUnit (`FrontieresDesContextesTest`) ; entrée `SeancesRealiseesDesCentresJdbc` dans `TableDuPatientCouvertureTest` ; `PatientStatsRestControllerTest` (constructeur du double) |
| D | RG-SEA-065 à 068 (`07-seances.md`), annexe 16 ; glossaires `proposed` (séance, pilotage-direction) ; §64 ; known-bugs ; cette fiche ; état `built` |

## Choix (vérifiés dans le code le 2026-11-08)
| Sujet | Choix | Raison |
|---|---|---|
| Maven | Injection directe des ports publiés dans la plateforme | `platform/pom.xml` dépend de `contexts-seance` ; la Séance ne dépend pas de la plateforme |
| Cahier de dialyse (jrxml) | La requête reste dans l'état Jasper ; lecteur NOMMÉ de `TableDesSeancesTest`, butoir 2027-06-30 (E-R3) | Modèle PERSONNALISABLE par centre (`ModeleDocumentPrinter.render` : version active de `modele_document_version`) ; une source de données issue d'un port ne servirait pas les versions des centres, changerait le contrat des modèles, EXCEL / HTML non rejoués. Mise en page et rendu inchangés : golden PDF-texte SE7e et `CahierDialyseReportIntegrationTest` verts |
| Écritures de la reprise | Port dédié `SeancesDeLaReprisePubliees`, mêmes gardes (liste blanche, centre du lot, date de création), réservé par ArchUnit aux 2 adapters de la reprise | Un seul écrivain de `seances` hors de la Séance ; aucune donnée modifiée hors du schéma de test (contrat : centres du contrat effacés avant et après, H2 en mémoire, PG16 jetable) |
| Libellé « seances » des bloquants | Constante `SeancesDeLaReprisePubliees.NATURE` | Observable (golden `reprise/*`) ; la plateforme ne garde plus aucun littéral de table |
| Garde KB-SEA-92 | Littéral `"seances"` rattaché à une requête construite (même fichier ou fichier qui nomme la classe) | Voit `ReferentialAdminTables` + `ReferentialAdminJdbcAdapter` ; faux positif nommé `MigrationEntity.java` (code d'URL) |

## Parité
| Élément | Résultat |
|---|---|
| Goldens SE7e (15) | Inchangés et verts, 3 exécutions après la bascule le 2026-11-08 (reprise après panne : 1re avec les tests ciblés, 2e seule, 3e en `-Dsurefire.runOrder=random`) ; aucun `.approved` modifié (`git status` vide sur `src/test/resources`) |
| Goldens et tests des modules (F5, A1, DM1, DM6, DM8, référentiels, reprise, Direction, cahier) | Inchangés et verts (exécution ciblée de 159 tests, puis suite complète) ; aucun `.approved.json` modifié ; aucun nouveau `.received` (les 14 `.received` ignorés présents datent d'avant SE7e) |
| Écarts | AUCUN (KB-SEA-83 à 91 reproduits ; KB-SEA-92 corrigé dans l'outillage de test seulement) |
| Flag / bascule | Aucun (ADR 0024) ; rollback = retour au commit précédent ; contrôle SELECT seule §64 |

## Exécutions (2026-11-08)
- Legacy avant tout code : SE7e 15/15.
- Contrat Séance : mémoire 13/13, H2 13/13 ; PG16 14/14 (avec la version) dans `seance-se7e-pg16-rejeu.sh 55549`.
- Rejeu PG16 : 19 vérifications des requêtes de production, 8 détections §64 (dont `flyway_schema_history`), 3 vérifications de `db/seed.sql`, contrat 14/14 ; **code 0**.
- Tests ciblés : architecture (`TableDesSeancesTest` 13, `FrontieresDesContextesTest` 43, `TableDuPatientCouvertureTest` 6, `TableDeLOrganisationTest` 4, `TableDesEquipementsDeLaGmaoTest` 3), `PatientStatsRestControllerTest` 5, `ReglesDeGestionDocumentationTest` 7, intégration et caractérisation liées 159 : verts.
- Suite complète `./mvnw -o test -fae` (depuis `backend/`, un build à la fois) : voir « Suite complète » ci-dessous.
- `check-arch` : 0 violation.

## Suite complète
`./mvnw -o test -fae` depuis `backend/`, 2026-11-08, UNE exécution, aucune relance : **7 717 tests, 0 échec, 0 erreur, 10 ignorés** (somme des 15 lignes « Tests run » des résumés Surefire par module : 22, 18, 154, 51, 32, 751, 310, 743, 203, 479 dont 1 ignoré, 395, 77, 724, 596, 3 162 dont 9 ignorés) ; 18 modules `SUCCESS`, `BUILD SUCCESS`. Ignorés préexistants : `SequenceConcurrenteJdbcTest` (1), `LectureSeuleDuPilotageTest` (2), `TableDuDossierMedicalTest` (3), `PgPlanAdapterPostgresTest` (4, PostgreSQL non configuré). Note : le travail des lots A à D avait été laissé non vérifié par une panne d'API ; toutes les exécutions de la section précédente ont été REFAITES ce jour avant de poser `built` (contrat Séance 26/26, ciblés 96/96 dont SE7e 15/15, PG16 code 0 avec contrat 14/14 sur le cluster jetable local `C:/Program Files/PostgreSQL/16`, Docker non disponible et non nécessaire).

## Résidus nominatifs (revue ≤ 2026-12-15 ET butoir propre)
| # | Résidu | Revue | Butoir |
|---|---|---|---|
| E-R1 | KB-SEA-83 à 87 : deux comptes de séances de la Direction, caisse sans centre, statistiques de tous statuts avec le volet d'un autre centre, libellés et précision du SGBD (décisions métier, DPO) | 2026-12-15 | 2027-03-31 |
| E-R2 | `SeancesRealiseesDesCentresJdbc` lit `patients`, `centre_payeur`, `agence`, `caisse_assurance` (entrée de `TableDuPatientCouvertureTest`) ; `StatistiquesDesSeancesJdbc` lit `prescriptions_medicales` (poids sec) : langages publiés du Patient et du Dossier médical à venir | 2026-12-15 | 2027-03-31 |
| E-R3 | `cahier_dialyse.jrxml` garde sa requête sur `seances` (modèle personnalisable) ; versions personnalisées actives hors du dépôt (§64.8) | 2026-12-15 | 2027-06-30 |
| E-R4 | `SeancesDeLaReprisePubliees` : port d'écriture générique (carte de champs) hors de l'agrégat `Seance` ; à remplacer par un cas d'usage de reprise de la Séance | 2026-12-15 | 2027-06-30 |
| E-R5 | KB-SEA-89 à 91 (reprise et référentiel sans centre ni statut) : décisions métier | 2026-12-15 | 2027-03-31 |
| E-R6 | `MigrationRollbackJdbcAdapter` lit encore `volet_medical` et `volet_paramedical` (tables de la Séance) en SQL pour « complétée » | 2026-12-15 | 2027-03-31 |
| E-R7 | Technique `NomsDeTableCalcules` appliquée à `TableDesSeancesTest` seulement ; faux positif nommé `MigrationEntity.java` | 2026-12-15 | 2027-03-31 |
| E-R8 | Non rejoués : instantanés et diffusion temps réel de la Direction (période par défaut = aujourd'hui), cahier EXCEL / HTML, sérialisation JSON de `poidsEvolution` sur PostgreSQL, concurrence ; couverture de branches et mutation non mesurées (PIT non lancé) ; PDF et DOCX de la documentation fonctionnelle (RG-SEA-065 à 068) à la prochaine régénération ordinaire (`docs/client/tools`) | 2026-12-15 | 2027-03-31 |

Restent en liste blanche pour SE7f (butoirs de `TableDesSeancesTest`) : `HistoriqueFourniLegacy`, `ForfaitsFournisLegacy` (2027-03-31), `db/seed.sql`, `db/demo/renadial-seed.sql`, `db/demo/renadial-purge.sql` (2027-06-30).

## Questions ouvertes
- **`db/seed.sql` rejoué au démarrage** (vérifié dans le code et rejoué sur PG16 jetable le 2026-11-08). (1) La prémisse « tant que `SEED_DATA_LOCATIONS` n'est pas défini » est inexacte : `application.yml:22` pose `spring.sql.init.mode: always` et `:26` `data-locations: ${SEED_DATA_LOCATIONS:classpath:db/seed.sql}` ; la variable ne coupe PAS le rejeu, elle REMPLACE le script (profil par défaut). (2) En production, `docker-compose.prod.yml:93` pose `SPRING_PROFILES_ACTIVE: prod` et `application-prod.yml:12-15` `spring.sql.init.mode: never` : ni `schema.sql` ni `seed.sql` ne sont exécutés, quelle que soit `SEED_DATA_LOCATIONS` (le job `.github/workflows/flyway.yml:175` pose aussi `prod`). (3) Risque résiduel : une instance démarrée sur la base de production SANS le profil `prod` (lancement manuel, `.env` incomplet) avec `DB_URL` vers PostgreSQL exécuterait `schema.sql` (DDL pensé pour H2, NON rejoué ici) puis `seed.sql`, et Hibernate en `ddl-auto: update` (`application.yml`, profil par défaut) ; `seed.sql` commence par `MERGE INTO societes … KEY (id)` (syntaxe H2, `seed.sql:3-7`) : rejoué sur PG16 jetable avec arrêt à la première erreur, il échoue dès cette instruction (« syntax error at or near "(" », ligne 7) et n'écrit RIEN (séances, sociétés, centres inchangés). Avec `continue-on-error` absent (faux par défaut, aucun `application*.yml` ne le pose), Spring devrait refuser de démarrer (hypothèse sur Spring, non rejouée avec Spring). Si `SEED_DATA_LOCATIONS` désignait un script compatible PostgreSQL, ses écritures (dont des séances) seraient appliquées à chaque démarrage. À trancher en SE7f (statut des scripts) ; contrôle d'exploitation proposé : vérifier que `SPRING_PROFILES_ACTIVE=prod` est posé sur toute instance branchée sur la base de production.
- Confirmation métiers / DPO de l'ADR 0099 (au plus tard 2026-12-15) ; exécuter §64 sur une copie de production (la requête 1 dit quelles migrations sont réellement appliquées).

## Reste à faire
Résidus E-R1 à E-R8 ; SE7f (fournisseurs `seance.v1`, scripts de données, retrait de bc-seance, garde finale) ; `/ddd-verify seance-se7e-lectures-pilotage-et-rapports`.
