---
slice: seance-se7f-retrait-bc-seance-et-garde
status: built
date: 2026-11-08
order: 7.6
context: seance (et dossier-medical, planification pour deux ports publiés)
parent: seance-se7-lectures-publiees
depends_on: [seance-se7a-routes-residuelles, seance-se7b-tableau-de-bord-mensuel, seance-se7c-lectures-facturation-stock, seance-se7d-lectures-absences-planification-dossier, seance-se7e-lectures-pilotage-et-rapports]
sources: [ADR 0100, .ddd/safety-net/seance-se7f.md, .ddd/safety-net/known-bugs.md (KB-SEA-93 à 98, KB-SEA-49), .ddd/migration/verifications-production.md §65, .ddd/safety-net/seams.md (SE7f lots C et D)]
---

# SE7f : retrait de `bc-seance`, volet paramédical dans la Séance, gardes finales des tables de la Séance et des analyses

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URL, mêmes rôles, aucun flag, aucune migration, aucun événement. Décisions : ADR 0100 (accepted, à confirmer métiers / médecins / DPO au plus tard le 2026-12-15). Audit vivant / mort : `.ddd/safety-net/seance-se7f.md`. La fiche précédente (« garde `@Disabled` à réactiver », 36 classes, 7 tests, 12 ACL à migrer) était périmée : voir l'audit (garde active à 6 entrées, 33 classes, 6 tests).

## Avant SE7f (rappel)
| Résidu | Vivant / mort | Devenu |
|---|---|---|
| `bc-seance` : `SeanceUseCase.getDetails`, `VoletParamedicalDomainService`, `VoletParamedical` (62 attributs, 15 persistés), `TensionArterielle`, `SeanceRepositoryPort.findById`, `VoletParamedicalRepositoryPort`, `VoletMedicalRepositoryPort` (lecture, effacement), `SeancePlanningPort`, `SeancePlacePort`, `ResultatAnalyseRepositoryPort.findByPatientId`, `CapaciteTheoriqueCalculator` | vivants | `contexts/seance` (volet paramédical, volets, planning, place), `contexts/dossier-medical` (`DerniereCreatininePubliee`), `contexts/planification` (`CapaciteTheoriqueDesCentres`) |
| `bc-seance` : le reste (création, scan, recherche, `ProgrammationSeance`, `SeanceSearch`, `SeanceBillingEligibilityPort`, `PlanningParametresPort`, `JourSemaine`…) et 4 tests | morts (KB-SEA-97) | supprimés |
| Plateforme : `VoletParamedicalRestController`, `UpsertVoletParamedicalRequest`, `SeanceApplicationService`, 4 entités JPA + 3 dépôts + 5 adaptateurs, `SeancePlanningJdbcAdapter`, `SeancePlaceJdbcAdapter`, `ResultatAnalyseSpecifications`, `SeanceBillingEligibilityParLaCouverture`, `PlanningParametresDuContexte` | 17 utilisateurs | supprimés ; `DomainServiceConfig`, `KdigoGreffeQueryService`, `DirectionCapaciteQueryService` édités (port) |
| Plateforme : 12 fournisseurs `infrastructure/acl/seance/*Legacy` | vivants | 8 retirés (lectures reprises dans la Séance), 2 édités (`SaisiesDeSeanceFourniesLegacy` réduit, `DiffusionsFournieesLegacy` + 1 méthode), 2 intacts (`FichesFournieLegacy`, `RappelsFournisLegacy`) |

## Lots livrés (commit filet `5c9a7a41` ; lots A à E : commit à venir, non commités par l'assistant)
| Lot | Contenu |
|---|---|
| Filet | `5c9a7a41` : audit, 6 goldens SE7f, rejeu PG16, KB-SEA-93 à 98 ; vert sur le legacy avant tout code |
| A | ADR 0100 ; ports publiés : `DerniereCreatininePubliee` (Dossier médical), `CapaciteTheoriqueDesCentres` (Planification) ; langage publié de la Séance réduit (8 ports `*Fourni(s)` retirés, `SaisiesDeSeanceFournies` réduit aux administrations, `DiffusionsFournies.voletParamedicalEnregistre`) |
| B | `contexts/seance` : domaine `VoletParamedical`, `MesuresDeSeance`, `Poids`, `TensionArterielle`, `VoletsParamedicaux`, `Seance.exigerUnVoletParamedical`, `MotifDeRefus.MESURE_INVALIDE`, `VoletsMedicaux.effacer` ; cas d'usage `RenseignerLeVoletParamedical` ; `VoletParamedicalControleur`, `VoletParamedicalDemande`, `ErreursDuVoletParamedicalAdvice` ; adaptateurs `VoletsParamedicauxJdbc`, `VoletsDeLaSeanceJdbc`, `PlanningDesPatientsJdbc`, `PlaceDuPatientJdbc`, `ForfaitsDeSeanceJdbc`, `HistoriqueDesSeancesJdbc`, `GenerateursDesPatientsJdbc`, `ForfaitsDuCentreJdbc`, `JourDuCentreJdbc`, `SaisiesParLeLegacy` (réécrit) ; contrats `VoletsParamedicauxContrat` (mémoire / H2 / PG16, 6 + 1 cas), `LecturesReprisesDeLaPlateformeContrat` (H2 / PG16, 20 cas), tests `VoletParamedicalTest` (7), `RenseignerLeVoletParamedicalTest` (9), `JourDuCentreJdbcTest` (2) ; `DerniereCreatinineContrat` (mémoire / H2 / PG16, 6 cas), `CapaciteTheoriqueTest` (7), `ConsulterLaCapaciteTheoriqueTest` (2) |
| C | 8 fournisseurs de la plateforme supprimés, 2 édités ; seam OUVERT puis FERMÉ, chemins exacts (`seams.md`) |
| D | `bc-seance` supprimé (`git rm -r`), `pom.xml`, `platform/pom.xml`, `Dockerfile` ; 17 utilisateurs de la plateforme ; `db/schema.sql` (4 tables H2) ; ArchUnit (`FrontieresDesContextesTest` : 5 règles de SE1/SE2/SE5/SE6 sur bc-seance remplacées par 5 règles SE7f) ; gardes `TableDesSeancesTest` (3 tables, `@Table`, 6 entrées), `TableDesAnalysesTest` (8 entrées), `TableDuPatientCouvertureTest`, `TableDesEquipementsDeLaGmaoTest`, `TableDeLaPlanificationTest`, `TableDeLOrganisationTest`, `EcrivainsDeLaFactureDesSeancesTest` (3 vers 2) ; 40 harnais de test (`JourSemaine` de test), `ParametrageDuPlanningPourLesTests` et `LecturesDeLaSeance*` (plateforme) supprimés |
| E | RG-SEA-069 à 071 (`07-seances.md`), annexe 16 ; glossaires `proposed` (séance, dossier-medical, planification) ; §65 ; known-bugs ; cette fiche ; `seance-se7f-pg16-rejeu.sh` étendu (§65 + 3 contrats PG16) ; état `built` (SE7f et SE7) |

## Choix (vérifiés dans le code le 2026-11-08)
| Sujet | Choix | Raison |
|---|---|---|
| Contexte des volets | Séance | cycle de vie de la séance (`UNIQUE (seance_id)`, suppression, `FACTUREE`) ; le médical y était déjà écrit |
| Contexte de `resultats_analyses` | Dossier médical ; seule la lecture « dernière créatinine » migre, par un port publié | écrivains déjà dans le Dossier médical ; aucun lien à une séance |
| Capacité théorique | Planification publie `CapaciteTheoriqueDesCentres` (calcul + base) ; la plateforme garde période, GMAO, masquage | la méthode est de la Planification ; `BaseDeLaCapaciteTheorique` devient interne (résidu F-R6) |
| Codes HTTP du volet | Advice dédié `ErreursDuVoletParamedicalAdvice` : 403 avant 400, 400, 409, 422 ; les corps illisibles restent en 500 `INTERNAL_ERROR` | parité stricte avec les goldens DM1 `erreurs` et `autres_mesures` (`ErreursDeSeanceAdvice` les passerait en 400 : écart non décidé) |
| KB-SEA-93 | Écriture validée puis fiche relue (unité de travail propre, diffusions après) | reproduit, décision métier à venir |
| 47 attributs non persistés | Non repris ; nommés dans l'ADR 0100 (décision 4) ; contrat HTTP inchangé | aucune saisie perdue (ni écran, ni colonne) |
| Fournisseurs restants | 4 dans la plateforme (fiches, diffusions, rappels, administrations), nommés (résidu F-R1) | `bc-patient` et le centre de notifications n'ont pas de langage publié ; le Dossier médical dépend de la Séance (cycle) |
| Schéma H2 | `db/schema.sql` crée les 4 tables que les entités JPA retirées généraient | comme P1 pour `planning_parametres` ; production inchangée (Flyway) |
| Maven | `contexts/seance` dépend de `contexts/planification` (`PlacesDesPatients`) | aucun cycle (`FrontieresDesContextesTest.la_planification_ne_depend_pas_de_la_seance`) |

## Parité
| Élément | Résultat |
|---|---|
| Goldens SE7f (6) | Inchangés et verts, 3 exécutions le 2026-11-08 (lot ciblé de 226 tests, puis seuls, puis `-Dsurefire.runOrder=random`) ; aucun `.approved` modifié, aucun `.received` |
| Goldens SE1 à SE7e, DM1, DM7, P6 (`planification/semaine`), PC3, PC4, `DirectionCapaciteIntegrationTest`, `FacturationRestControllerIntegrationTest`, cahier, KDIGO | Inchangés et verts (226 tests ciblés, 31 classes, 0 échec), puis suite complète |
| Écarts | AUCUN (KB-SEA-93 à 96 reproduits ; KB-SEA-97, 98 clos : code mort et outillage ; KB-SEA-49 clos par retrait) |
| Flag / bascule | Aucun (ADR 0024) ; rollback = retour au commit précédent ; contrôle SELECT seule §65 |

## Exécutions (2026-11-08)
- Legacy avant tout code : SE7f 6/6 (commit `5c9a7a41`, 3 exécutions).
- `contexts/seance` : 771 tests, 0 échec (dont contrats volets paramédicaux mémoire 6, H2 7, lectures reprises H2 20 ; PG16 rejoué par le script).
- `contexts/planification` et `contexts/dossier-medical` ciblés : 20 et 29 tests (6 PG16 ignorés hors script), 0 échec.
- Plateforme ciblée : gardes d'architecture 93 puis 23 (corrections), parité 226, SE7f 6 + 6.
- Rejeu PG16 `seance-se7f-pg16-rejeu.sh 55550` : F1 à F6 (15 vérifications), §65 (5 détections), contrats PG16 (`VoletsParamedicauxPostgresContractIT` 6, `LecturesReprisesDeLaPlateformePostgresContractIT` 20, `DerniereCreatininePostgresContractIT` 6) : voir « Suite complète » pour le code de sortie.
- `check-arch` : 0 violation. `./mvnw -o -q compile` du réacteur sans `bc-seance` : OK ; `Dockerfile` : aucun `COPY` orphelin.

## Suite complète
`./mvnw -o test -fae` depuis `backend/`, lancé le 2026-11-08 à 22 h 59 UTC (un seul build) : **7 732 tests, 10 échecs, 0 erreur, 13 ignorés** (somme des 14 lignes « Tests run » des résumés Surefire par module, fins de ligne Windows retirées : 22, 18, 32, 751, 154, 310, 752, 203, 479 dont 1 ignoré, 395, 77, 771, 608, 3 160 dont 12 ignorés) ; 16 modules `SUCCESS` (dont `contexts-seance` 771, `contexts-planification` 752, `contexts-dossier-medical` 608), `platform` `FAILURE`. SE7e comptait 7 723 tests (puis 7 723 avec le filet SE7f) ; `bc-seance` et ses 6 tests ont disparu (−32 : `CapaciteTheoriqueCalculatorTest` 7 et `TensionArterielleTest` repris, `SeanceDomainServiceTest` 9, `ProgrammationSeanceTest` 8, `SeanceDeverrouillageTest` 5, `VoletParamedicalDomainServiceTest` 2 partis avec le code mort), +41 tests des contextes et de la plateforme (`VoletParamedicalTest` 7, `RenseignerLeVoletParamedicalTest` 9, contrats volets 6 + 7, lectures reprises 20, `JourDuCentreJdbcTest` 2, `CapaciteTheoriqueTest` 7, `ConsulterLaCapaciteTheoriqueTest` 2, `DerniereCreatinine*` 12, `TableDesAnalysesTest` 5, +2 règles ArchUnit…).
Les **10 échecs** sont tous dans deux classes de tests legacy de la plateforme NON touchées par la slice, `SeanceScanIntegrationTest` (9) et `SeanceDeverrouillageIntegrationTest` (1) : elles posent les jours de dialyse et la séance « du jour » avec `LocalDate.now()` de la JVM (UTC depuis `BackendApplication`), alors que le jour du centre est celui d'`Africa/Algiers` ; le build a traversé la fenêtre 23 h–24 h UTC (00 h 07 locale au moment des échecs : « Ce patient n'est pas programmé aujourd'hui »), où les deux jours diffèrent. Cause étrangère à SE7f (le jour du centre était déjà celui du centre depuis SE1, KB-SEA-01 ; la logique de `JourDuCentreJdbc` est celle de `JourDuCentreLegacy`), fragilité connue des harnais (`supposerLeJourDeLaJvmEgalAuJourDuCentre` dans PC3/PC4). Relance honnête : les deux classes seules, après 00 h 00 UTC, résultat consigné ci-dessous.
Ignorés préexistants : `SequenceConcurrenteJdbcTest` (1), `LectureSeuleDuPilotageTest` (2), `TableDuDossierMedicalTest` (3), `PgPlanAdapterPostgresTest` (4, PostgreSQL non configuré) ; nouveaux ignorés hors script PG16 : `DerniereCreatininePostgresContractIT` (6, joués par `seance-se7f-pg16-rejeu.sh`, 6/6) — les contrats PG16 de la Séance (`VoletsParamedicauxPostgresContractIT`, `LecturesReprisesDeLaPlateformePostgresContractIT`) ne sont pas comptés par Surefire (suffixe `IT`), joués par le script (26/26). Rejeu PG16 `seance-se7f-pg16-rejeu.sh 55550` : F1 à F6 (18 vérifications), §65 (5 détections), contrats 26 + 6 ; **code 0**. `check-arch` : 0 violation ; `./mvnw -o -q compile` du réacteur : OK ; `Dockerfile` : aucun `COPY` orphelin. Aucun `.approved` modifié, aucun `.received` (goldens SE1 à SE7f, DM1, DM7, P6, PC3, PC4 : 226 tests ciblés + suite complète).

### Relance des deux classes après la fenêtre de minuit UTC
Lancée le 2026-11-09 à 00 h 02 UTC (`-pl platform -Dtest=SeanceScanIntegrationTest,SeanceDeverrouillageIntegrationTest`, un seul build, aucune autre modification entre les deux exécutions) : **18 tests, 0 échec** (13 + 5), `BUILD SUCCESS`. Bilan de la suite complète corrigé par cette relance : 7 732 tests, 0 échec, 0 erreur, 13 ignorés. La fragilité de ces deux classes (jour de la JVM contre jour du centre) est préexistante et hors périmètre : à traiter comme les harnais PC3/PC4 (`supposerLeJourDeLaJvmEgalAuJourDuCentre`) lors de leur reprise.

## Résidus nominatifs (revue ≤ 2026-12-15 ET butoir propre)
| # | Résidu | Revue | Butoir |
|---|---|---|---|
| F-R1 | 4 fournisseurs de `seance.v1` dans la plateforme : `FichesFournieLegacy` (fiche complète, code, numéro d'assurance par `bc-patient`), `DiffusionsFournieesLegacy`, `RappelsFournisLegacy` (notifications), `SaisiesDeSeanceFourniesLegacy` (administrations, `bc-medical`) | 2026-12-15 | 2027-03-31 |
| F-R2 | Lectures transitoires de la Séance : `patients`, `prise_en_charge`, `forfait`, `gmao_equipements`, `deplacement_temporaire`, `centers` (inscrites dans les gardes des contextes propriétaires) | 2026-12-15 | 2027-03-31 |
| F-R3 | KB-SEA-93, 94, 95, 96 reproduits (métier, médecins, DPO) ; §65.1 à exécuter sur une copie AVANT bascule | 2026-12-15 | 2027-03-31 |
| F-R4 | `MigrationRollbackJdbcAdapter` lit les volets (E-R6, inscrit) ; `HistoricalRecordJdbcAdapter` écrit des volets par un nom calculé non détecté par la garde | 2026-12-15 | 2027-03-31 |
| F-R5 | 47 attributs legacy non persistés du volet : fonction manquante ou dette (ADR 0100, décision 4) | 2026-12-15 | 2027-03-31 |
| F-R6 | `BaseDeLaCapaciteTheorique` : port publié sans consommateur externe, à rétrograder | 2026-12-15 | 2027-03-31 |
| F-R7 | Non rejoués : concurrence sur le volet paramédical (409 PostgreSQL), couverture / mutation, PDF et DOCX de la documentation fonctionnelle | 2026-12-15 | 2027-03-31 |
| F-R8 | `db/seed.sql`, `renadial-seed.sql`, `renadial-purge.sql` (données, rejoués hors profil `prod` : question ouverte SE7e maintenue) ; `cahier_dialyse.jrxml` (E-R3) ; `db/schema.sql` (schéma H2, inscrit) | 2026-12-15 | 2027-06-30 |

## Questions ouvertes
- KB-SEA-93 : une seule transaction (écriture et diffusions) ou garde de la fiche avant l'écriture ?
- KB-SEA-94 : réparer les volets posés sous un autre centre que leur séance et contraindre, ou lire par séance seule ? (§65.1 sur une copie avant bascule)
- KB-SEA-95 : motif « jour férié » pour le scan de l'administrateur ?
- KB-SEA-96 : départage à date égale, traitement de la créatinine à 0 (médecins).
- Les 47 attributs non persistés du volet legacy (dont la signature de l'infirmier et l'abord vasculaire) : fonction attendue ?
- Confirmation métiers / médecins / DPO de l'ADR 0100 au plus tard le 2026-12-15.

## Reste à faire
Résidus F-R1 à F-R8 ; `/ddd-verify seance-se7f-retrait-bc-seance-et-garde` (et `seance-se7-lectures-publiees`, toutes les SE7x étant `built`).
