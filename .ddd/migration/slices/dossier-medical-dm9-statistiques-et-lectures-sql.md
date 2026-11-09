---
slice: dossier-medical-dm9-statistiques-et-lectures-sql
status: built (lots A à E livrés le 2026-11-09 ; un écart assumé KB-MED-110, golden `_nouveau_code` approuvé sur décision du coordinateur, legacy archivé)
date: 2026-11-09
order: 9
context: dossier-medical (et planification pour deux ports fournis, plateforme pour la présentation)
sources: [ADR 0101, .ddd/safety-net/dossier-medical-dm9.md (audit), .ddd/safety-net/known-bugs.md (KB-MED-100, 104, 106 à 109, décisions DM9), .ddd/migration/verifications-production.md §66-67, .ddd/safety-net/seams.md (DM9 lots C et D), ADR 0087, 0100 (modèle)]
---

# DM9 : statistiques médicales, indicateurs Direction, KDIGO et PDF de greffe, lecteurs SQL de la Planification, retrait de `bc-medical`

Refactoring SUR PLACE (ADR 0024/0072) : mêmes tables, mêmes URL, mêmes rôles, mêmes codes HTTP ; aucun flag, aucune façade, aucune projection, aucun événement, aucune migration. Décisions : ADR 0101 (accepted, à confirmer métiers / médecins / DPO au plus tard le 2026-12-15). Audit vivant / mort : `.ddd/safety-net/dossier-medical-dm9.md`. **`volet_medical` appartient à la Séance (ADR 0100)** : la mention « lu ici » de l'ancienne fiche était fausse (audit §4), rien n'était à migrer ; `TableDesSeancesTest` (SE7f) garde déjà `volet_medical` et `volet_paramedical`.

## Avant DM9 (rappel) → devenu
| Résidu | Vivant / mort | Devenu |
|---|---|---|
| `bc-medical` : `KdigoEvaluationPolicy`, `KdigoGreffeEvaluationPolicy` + 8 objets-valeurs ; `findByPatientId` de `Serologie`, `Antecedent`, `BilanPreGreffe`, `EtapeBilanPreGreffe`, `DonneurVivant` ; `AdministrationTraitementRepositoryPort.deleteBySeanceId` ; 3 tests | vivants | `contexts/dossier-medical` : `CiblesKdigo`, `EvaluationKdigoDuCandidat` (+ `RegleDeCible`, `StatutDeCible`, `RisqueImmunologique`, `NiveauDeRisqueKdigo`, `FonctionRenale`, `StadeDeMaladieRenale`, `AlerteSerologiqueKdigo`), cas d'usage `EvaluerLeCandidatALaGreffe`, `ConstituerLeDossierDeGreffe`, port `Antecedents.tousDuPatient`, `Administrations.effacerCellesDeLaSeance` ; tests `CiblesKdigoTest`, `EvaluationKdigoDuCandidatTest` (portés) |
| `bc-medical` : 3 `*Event`, `PeriodeClinique`, `TransitionStatutBilanGreffeSpecification`, méthodes `save` / `deleteById` / `findById` / `findPagedByPatientId` / `existsBy…` des 6 adaptateurs, `ConceptCodeTest` (déjà porté en `CodeCliniqueTest`) | morts | supprimés avec le module (`git rm -r bc-medical`) ; `EvaluationCibleResponse` : aucun appelant, confirmé mort, supprimé |
| Plateforme : `KdigoGreffeQueryService`, `KdigoGreffeRestController`, 5 DTO `*Response`, 6 adaptateurs, 7 dépôts JPA, 17 entités JPA, `SaisiesDeSeanceFourniesLegacy` | 14 utilisateurs + schéma H2 | supprimés ; `GreffePdfService`, `PatientStatsQueryService`, `DirectionIndicatorsQueryService`, `DirectionBreakdownQueryService`, `GreffeExportRestController` édités (ports publiés, plus aucun SQL médical) ; `db/schema.sql` crée les 17 tables (V1, V2, V31, V53) |
| Planification : `RisquesInfectieuxJdbc` (SQL `serologies_patient`), `ContraintesDesPatientsJdbc:34-37` (SQL `abords_vasculaires`) | vivants | `RisquesInfectieuxPublies` (ACL sur `RisquesInfectieuxFournis`), `ContraintesDesPatientsJdbc(jdbc, AbordsVasculairesFournis)` ; `PlacesDuCentreJdbc`, `SejoursDesPatientsJdbc`, `SeancesRealiseesPubliees` reçoivent le port fourni ; traducteurs plateforme `infrastructure/acl/planification/{RisquesInfectieux,AbordsVasculaires}ParLeDossierMedical` (cycle Maven évité : le Dossier médical dépend déjà de la Planification) |

## Lots livrés (commit filet `7a16c21a` ; lots A à E : non commités par l'assistant)
| Lot | Contenu |
|---|---|
| A | ADR 0101 ; ports publiés du Dossier médical : `RisquesInfectieuxPublies`, `AbordsVasculairesPublies`, `StatistiquesMedicalesPubliees`, `IndicateursCliniquesPublies`, `TraitementDeLAnemiePublie`, `DossierDeGreffePublie` ; ports fournis de la Planification : `RisquesInfectieuxFournis`, `AbordsVasculairesFournis` |
| B | Domaine : `CiblesKdigo`, `EvaluationKdigoDuCandidat`, `EffectifMasque`, `RepartitionFaceALaCible`, `RisqueInfectieux` (+ objets-valeurs) ; `EvaluationDeCible` (DM6) s'appuie sur `CiblesKdigo` (`StatutDeCible` typé). Application : `EvaluerLeCandidatALaGreffe`, `ConstituerLeDossierDeGreffe`, `CalculerLesIndicateursCliniques`, port `LecturesDesIndicateursCliniques`. Adaptateurs (SQL mot pour mot, cité `chemin:ligne`) : `PatientsARisqueInfectieuxJdbc`, `PorteursDUnCatheterJdbc`, `StatistiquesMedicalesJdbc`, `IndicateursCliniquesJdbc`, `TraitementDeLAnemieJdbc`, `AdministrationsJdbc.effacerCellesDeLaSeance`, `AntecedentsJdbc.tousDuPatient` ; ACL `acl/seance/SaisiesDeSeanceParLeDossierMedical`. Interface : `EvaluationKdigoControleur`, `EvaluationKdigoReponse` (advice étendu). Configuration : `IndicateursEtStatistiquesConfiguration`. Tests : `CiblesKdigoTest` 4, `EvaluationKdigoDuCandidatTest` 5, `RepartitionFaceALaCibleTest` 4, `RisqueInfectieuxTest` 5, `EvaluerLeCandidatALaGreffeTest` 4, `ConstituerLeDossierDeGreffeTest` 3, `CalculerLesIndicateursCliniquesTest` 3, contrats `RisquesInfectieuxContrat` (mémoire 3, H2 3, PG16 3), `LecturesReprisesDeLaPlateformeContrat` (H2 8, PG16 8), `AdministrationsContrat` +1 (mémoire, H2, PG16) |
| C | Plateforme sur les ports (5 fichiers édités, 2 traducteurs créés) ; suppression des 14 utilisateurs ; Planification sur les lectures fournies ; tests Planification (`RisquesInfectieuxDeTest`), Absences (constructeur) adaptés |
| D | `db/schema.sql` (17 tables) ; `bc-medical` retiré (`pom.xml`, `platform/pom.xml`, `Dockerfile`) ; `TableDuDossierMedicalTest` réécrite ACTIVE (17 tables, `@Table`, `CREATE TABLE`, listes, noms calculés ; 10 entrées nominatives datées, compte asserté) ; `TableDesAnalysesTest` 8 → 6 ; `FrontieresDesContextesTest` (3 règles DM7/DM8 remplacées par 3 règles DM9) ; `DirectionAlertPolicyTest` (2 cas déplacés dans le module) ; harnais DM9 `planification` sur `RisquesInfectieuxFournis` |
| E | RG-MED-049f (+ RG-MED-049 mise à jour) dans `11-dossier-medical.md`, annexe 16 ; glossaires `proposed` (dossier-medical : 8 lignes, planification : 2) ; `verifications-production.md` §67 ; known-bugs (décisions DM9) ; documentation fonctionnelle HTML (3 passages, à la main) ; `dossier-medical-dm9-pg16-rejeu.sh` étendu (4 contrats PG16) ; cette fiche ; seams fermés |

## Choix (vérifiés dans le code le 2026-11-09)
| Sujet | Choix | Raison |
|---|---|---|
| Qui calcule, qui présente | Le contexte calcule tout (ports publiés) ; la plateforme garde PDF (openhtmltopdf 1.0.10), CSV, JSON, période / société / stock de la Direction | option hybride 9.2 b + 9.5 c de l'audit ; goldens DM7, DM8, DM9 inchangés |
| Contrôleur KDIGO | déplacé dans le module (`EvaluationKdigoControleur`, même URL, champs, rôles) | trivial : aucun PDF ni CSV ; la garde du patient passe dans le cas d'usage |
| Garde du patient du PDF | dans `ConstituerLeDossierDeGreffe` (404 rendu par l'advice du contexte via `SousLaGardeDuDossierMedical`) ; `GreffeExportRestController` ne garde plus seul | décision 5 ; `GardeDePatient` reste publié (aucun autre consommateur : résidu M-R5) |
| Anonymat | `EffectifMasque` (module) pour les indicateurs cliniques, `AnonymityPolicy` (plateforme) pour les autres tableaux | deux contextes, mêmes valeurs, décision explicite (pas une factorisation) |
| Règle des 18 ans (`PEDIATRIE`) | reste dans `ContraintesDesPatientsJdbc` (Planification, table `patients`) | règle de la Planification (RG-PLN-097) sur une table de Patient et couverture : hors Dossier médical (dévie de la liste « domaine riche » du briefing, expliqué) |
| Cycle Maven planification → dossier-medical | évité : ports `*Fournis` dans `planification.publishedlanguage`, traducteurs dans la plateforme | le Dossier médical dépend déjà de la Planification (DM4) |
| Horloge de l'âge KDIGO | `horlogeDuDossierDeBase` (UTC, JVM) | parité stricte avec `LocalDate.now()` du legacy |
| Schéma H2 | 17 `CREATE TABLE IF NOT EXISTS` ; `dossier_medical_patient` : `UNIQUE (patient_id)` identique à V1 et à l'entité retirée | aucun test ne dépendait d'un écart |

## Parité
| Élément | Résultat |
|---|---|
| Goldens DM1 à DM9, SE6, SE7e, SE7f, Planification, Direction (lot ciblé) | **918 tests, 1 échec**, 0 erreur (117 classes, 2026-11-09) ; aucun `.approved` modifié ; UN SEUL `.received` nouveau (ci-dessous) ; les autres `.received` présents dans l'arbre sont antérieurs à la slice (absences-patients, se5, se6 : non touchés) |
| **Écart assumé (KB-MED-110)**, décision du coordinateur du 2026-11-09, à confirmer métiers ≤ 2026-12-15 | DM7 `lecteurs/l_export_pdf_a_une_ligne_illisible_e/pdf_ligne_illisible` : le legacy répondait `400 BAD_REQUEST \| No enum constant com.hemodialyse.backend.domain.medical.greffe.valueobject.CategorieEtapeGreffe.CATEGORIE_DISPARUE` (et `StatutBilanGreffe.STATUT_DISPARU` pour le bilan et KDIGO) : le message figeait le NOM QUALIFIÉ d'une classe du module retiré, irreproductible par construction, sur une donnée corrompue. Legacy archivé octet pour octet dans `dm7/_ecarts-legacy/lecteurs/pdf_ligne_illisible_avant_dm9.legacy.json` ; golden `pdf_ligne_illisible_nouveau_code` approuvé après lecture du reçu (étape illisible ignorée `200 ok`, bilan illisible `422 BILAN_PRE_GREFFE_ILLISIBLE` pour le PDF et KDIGO : règles DM7, RG-MED-046 d et 047 e) ; jamais `-Dcharacterization.approve=true` |
| Module `contexts/dossier-medical` | nouveaux tests verts (68 ciblés) ; suite complète : voir ci-dessous |
| `check-arch.ts` | 0 violation (0 baselined, 0 new) |
| `./mvnw -o -q compile` du réacteur sans `bc-medical` | OK ; `Dockerfile` : aucun `COPY <module>/pom.xml` orphelin (les deux `COPY --from=` sont des étapes multi-stage) |
| Flag / bascule | aucun (ADR 0024) ; rollback = retour au commit précédent |

## Suite complète et rejeu PG16
`./mvnw -o test -fae` depuis `backend/`, lancé le 2026-11-09 à 05 h 58 UTC, fini à 06 h 08 (un seul build) : **7 755 tests, 2 échecs, 0 erreur, 7 ignorés** (somme des 13 lignes « Tests run » des résumés Surefire : 22, 18, 751, 154, 310, 752, 203, 479 dont 1 ignoré, 395, 77, 771, 652, 3 171 dont 6 ignorés) ; 15 modules `SUCCESS` (dont `contexts-dossier-medical` 652 = 608 + 44 nouveaux, `contexts-planification` 752), `platform` `FAILURE`. SE7f comptait 7 732 tests ; `bc-medical` et ses 3 tests (23) ont disparu, +44 tests du module (domaine 18, application 10, contrats 16), +1 règle ArchUnit nette, +1 test de garde (`TableDuDossierMedicalTest` 6 actifs, 3 `@Disabled` retirés).
Les **2 échecs** : (1) DM7 `pdf_ligne_illisible` : l'écart attendu et NON approuvé (ci-dessus) ; (2) `ControleDesPeremptionsCharacterizationIntegrationTest.les_evenements_lot_proche_expiration_v1_sont_livres_au_consommateur` (Stock, classe NON touchée par DM9 : les événements de péremption reçus en double dans la suite complète, interférence d'ordre entre tests) ; **relancée seule** après le rejeu PG16 : 7 tests, 0 échec, `BUILD SUCCESS`, aucun `.received`. Bilan corrigé : 7 755 tests, 1 échec (l'écart nominatif), 0 erreur, 7 ignorés (préexistants : `SequenceConcurrenteJdbcTest` 1, `LectureSeuleDuPilotageTest` 2, `PgPlanAdapterPostgresTest` 4 ; les 3 `@Disabled` de l'ancienne `TableDuDossierMedicalTest` n'existent plus).
Rejeu PostgreSQL 16 `dossier-medical-dm9-pg16-rejeu.sh 55551` (cluster jetable, mot de passe `DM9_PG16_PASSWORD`, chaîne Flyway de 51 fichiers) : M1 à M6 et §66.1 à 66.5 : **17 vérifications OK**, puis contrats PG16 `AdministrationsPostgresContractIT` 4, `DerniereCreatininePostgresContractIT` 6, `LecturesReprisesDeLaPlateformePostgresContractIT` 8, `RisquesInfectieuxPostgresContractIT` 3 : **21 tests, 0 échec ; code 0**. `check-arch.ts` : 0 violation ; `./mvnw -o -q compile` : OK ; `Dockerfile` sans `COPY` orphelin ; `seamAllowed` remis à `[]`, chemins supprimés retirés de `covered.json`, les deux traducteurs créés y sont inscrits.

## Résidus nominatifs (revue ≤ 2026-12-15 ET butoir propre)
| # | Résidu | Revue | Butoir |
|---|---|---|---|
| M-R1 | Deux ACL de notification dans la plateforme : `infrastructure/acl/dossiermedical/NotificationsDeLAnemieLegacy`, `SignalementDesPrescriptionsLegacy` (le centre de notifications n'a pas de langage publié) | 2026-12-15 | 2027-03-31 |
| M-R2 | Lecteurs des tables du Dossier médical hors contexte, inscrits dans `TableDuDossierMedicalTest` (10) : `HistoricalRecordJdbcAdapter`, `MigrationRollbackJdbcAdapter`, `ReferentialAdminTables` (comptage des prescriptions d'un médecin), `cahier_dialyse.jrxml`, `ordonnance.jrxml`, `StatistiquesDesSeancesJdbc` (Séance, poids sec : à remplacer par `StatistiquesMedicalesPubliees.poidsSecCibleEnVigueur`), `schema.sql`, `seed.sql`, `renadial-seed.sql`, `renadial-purge.sql` | 2026-12-15 | 2027-03-31 / 2027-06-30 |
| M-R3 | KB-MED-106 (RISQUE CLINIQUE), 107, 108, 109, 104 (DPO), 100, 94 reproduits ; valeurs KDIGO non validées | 2026-12-15 | 2027-03-31 |
| M-R4 | KB-MED-110 : écart assumé `pdf_ligne_illisible_nouveau_code` (étape illisible ignorée, bilan illisible 422) à confirmer par les métiers | 2026-12-15 | 2027-03-31 |
| M-R5 | `GardeDePatient` publié sans consommateur après DM9 (`SousLaGardeDuDossierMedical` reste utilisé par `GreffeExportRestController`) : à rétrograder | 2026-12-15 | 2027-03-31 |
| M-R6 | Filet du Pilotage : les goldens SE7e ne figent les administrations d'anémie qu'à zéro ; `TraitementDeLAnemieJdbc` est tenu par le contrat H2/PG16 seulement | 2026-12-15 | 2027-03-31 |
| M-R7 | `ContraintesDesPatientsJdbc` lit encore `patients` (inscrit dans `TableDuPatientCouvertureTest`) ; `PatientStatsQueryService` et `GreffePdfService` lisent la fiche par `bc-patient` (`PatientRepositoryPort`) | 2026-12-15 | 2027-03-31 |
| M-R8 | PDF et DOCX de la documentation fonctionnelle à régénérer (HTML édité à la main) ; couverture et mutation non mesurées | 2026-12-15 | 2027-03-31 |

## Questions ouvertes
1. **KB-MED-106 (médecins, PRIORITÉ, risque clinique)** : un résultat DOUTEUX ou EN_COURS doit-il conserver le dernier résultat tranché au lieu de lever l'isolement ?
2. KB-MED-104 (DPO, condition de mise en production) : options a à e de l'ADR 0101.
3. KB-MED-100 (DPO) et retrait des trois `POST /api/v1/patients/stats/*/search` sans appelant frontend (métier).
4. KB-MED-107, 108 (métiers, Transfert patient), 109 (médecins, Direction) ; KB-MED-94 ; valeurs de référence KDIGO.
5. KB-MED-110 : confirmation métiers de l'écart assumé.

## Reste à faire
`/ddd-verify dossier-medical-dm9-statistiques-et-lectures-sql` ; résidus M-R1 à M-R8 ; confirmations métiers / médecins / DPO au plus tard le 2026-12-15.
