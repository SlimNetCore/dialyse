---
slice: patient-couverture-pc5-lectures-publiees
status: built
date: 2026-11-04
order: 5
context: patient-couverture
sources: [.ddd/tactical/patient-couverture.md, ADR 0024, 0064, 0076, 0077, 0078, backend/contexts/patient-couverture/GEL.md]
---

# PC5 — Lectures publiées et retrait du legacy Patient & couverture

> Statut vrai au 2026-11-04 : **`built`** (S1, S2 et S3 faites ; ADR 0079 `accepted`, jalons acceptés par l'utilisateur). Refactoring SUR PLACE (ADR 0024) : comportement INCHANGÉ, parité stricte, aucun golden `_nouveau_code` attendu ; les filets PC1–PC4, Planification, Absences, Facturation, Séance, Direction et Stock sont la preuve et restent verts SANS modification de golden.

## Langage publié `patientcouverture.v1` (convention : interfaces de `publishedlanguage`, `UUID` et `java.time`, bornées au centre, records imbriqués, comme `PresenceDesPatients` de la Planification)

| Port | Méthodes (lectures par intention) | Remplace (SQL direct) |
|---|---|---|
| `FichesDesPatients` | `existe(centre, patient)`, `noms(centre, patients)`, `situation(centre, patient)` (inconnu / suivi / parti) | Absences `PatientsDuCentreJdbc`, `NomsDesPatientsJdbc` ; Planification `NomsDesPatientsJdbc`, `PatientsDuCentreJdbc` |
| `CouvertureDesPatients` | `forfaitApplicable(centre, patient, date)`, `prisesEnChargeTarifees(centre, patient)`, `causeDeNonFacturation(centre, patient, jour)`, `pecValidee(centre, patient, pec)` | Absences `ForfaitsDesPatientsJdbc` ; Facturation `SeancesFacturablesJdbc` (partie PEC) ; Séance `SeanceBillingEligibilityJdbcAdapter`, `PecValidationJdbc` |
| `CompteursDeCouverture` | `prisesEnCharge`, `prisesEnChargeValideesAEcheance`, `attestations`, `attestationsAEcheance`, `patientsCrees`, `patientsPresentsLe` | `DashboardRestController` |

Adapters (module, SQL repris À L'IDENTIQUE) : `FichesPublieesJdbc`, `CouvertureDesPatientsJdbc`, `CompteursDeCouvertureJdbc`, câblés dans `PatientCouvertureConfiguration`. Contrat : `LecturesPublieesContrat` (14 tests) passe sur le double `LecturesPublieesEnMemoire`, sur H2 et sur PostgreSQL 16 (`patient-couverture-pc5-pg16-rejeu.sh`).

## Inventaire exhaustif des lecteurs et écrivains SQL hors module (grep multiligne `FROM|JOIN|INTO|UPDATE|TABLE` sur les six tables, 23 fichiers de `src/main`, relevé du 2026-11-04)

| # | Fichier | Tables | Accès | Décision |
|---|---|---|---|---|
| 1 | Absences `legacypatient/PatientsDuCentreJdbc` | `patients` | lit | **S2 : migre** vers `FichesDesPatients.existe` |
| 2 | Absences `legacypatient/NomsDesPatientsJdbc` | `patients` | lit | **S2 : migre** (`noms`) |
| 3 | Absences `legacycouverture/ForfaitsDesPatientsJdbc` | `prise_en_charge`, `forfait` | lit | **S2 : migre** (`forfaitApplicable`) |
| 4 | Planification `legacypatient/NomsDesPatientsJdbc` | `patients` | lit | **JALON : dépendance Maven CIRCULAIRE** — `contexts-patient-couverture` dépend de `contexts-planification` (la place, ADR 0052) ; Planification ne peut pas dépendre du langage publié du module sans cycle. Le port `noms` est prêt ; levée possible en extrayant les interfaces publiées dans un module d'API partagé (hors périmètre) |
| 5 | Planification `legacypatient/PatientsDuCentreJdbc` | `patients` | lit | **JALON : même cycle Maven** (le port `situation` est prêt) |
| 6 | Facturation `legacyseance/SeancesFacturablesJdbc` | `seances`, `patients`, `prise_en_charge` | lit | **S2 : migre la partie PEC** (`prisesEnChargeTarifees`) ; la jointure séances-patients (tri `p.nom, p.prenom`, payeur) reste : jalon, jointure de Facturation avec ses propres tables, découper la jointure changerait le tri |
| 7 | Séance `SeanceBillingEligibilityJdbcAdapter` (plateforme) | `prise_en_charge`, `attestation_droit` | lit | **S2 : migre** (`causeDeNonFacturation`) ; le port `SeanceBillingEligibilityPort` n'est pas modifié |
| 8 | Séance `contexts/seance/.../PecValidationJdbc` | `prise_en_charge` | lit | **JALON : code MORT** — `contexts/seance` est gelé et HORS réacteur (ADR 0074, non compilé, aucun filet) ; le port `pecValidee` est prêt pour sa reprise |
| 9 | `DashboardRestController` | `patients`, `prise_en_charge`, `attestation_droit` | lit | **S2 : migre** (`CompteursDeCouverture`) |
| 10 | Facturation `persistence/SituationsDesFacturesJdbc` | `factures`…, `patients` | lit | **JALON** : jointure de six tables de Facturation avec `patients` dans un `GROUP BY` ; la découper changerait groupement et tri (KB-FAC-49) ; contexte Facturation |
| 11 | Direction `DirectionDashboardQueryService`, `DirectionBreakdownQueryService` | `patients` | lit | **JALON** : agrégats SQL de la Direction avec le prédicat d'effectif (`EffectifSql`) dans des `GROUP BY` ; contexte Pilotage |
| 12 | `SeanceRestController` (1300 lignes) | `patients`, `prise_en_charge` | lit | **JALON** : tableau de bord et journal de la Séance, hors ports ; contexte Séance |
| 13 | `SeancePlanningJdbcAdapter` (plateforme) | `patients` | lit | **JALON** : lit des colonnes de PLACE (jours de dialyse) propriété de la Planification (ADR 0052) |
| 14 | Planification `ContraintesDesPatientsJdbc` | `patients` | lit | **JALON** : transporteurs et date de naissance du patient pour les compétences exigées ; lecture croisée Planification (dossier médical), pas une lecture de fiche publiée |
| 15 | Planification `SejoursDesPatientsJdbc`, `PlanningDesPlacesDuCentreJdbc`, `LectureDesPreferencesJdbc`, `SeancesRealiseesJdbc` | `patients` | lit | **JALON** : colonnes de place (salle, créneau, générateur, jours) propriété de la Planification (ADR 0052), mêlées à l'état |
| 16 | Planification `PlacesDuCentreJdbc`, `PlacesTenuesJdbc`, `MouvementsDesPatientsJdbc` | `patients`, `mouvement_patient` | **écrivent** | **JALON** : la Planification écrit la place (ADR 0052) et le mouvement `PLACE_LIBEREE` ; inverser la propriété est hors périmètre |
| 17 | `MigrationRollbackJdbcAdapter` (+ `HistoricalRecordJdbcAdapter`, migrateurs, `bc-assure`) | `patients`, `assure`, `assure_patient`, `attestation_droit` | lit / écrit | **JALON** : outil de migration, à lancer hors activité du centre (ADR 0077 point 12, ADR 0078 point 10) ; décision (3) : `bc-assure` reste tant que les migrateurs JPA le référencent |
| 18 | `JourDuCentreLegacy` (plateforme) | `centers` | lit | **JALON** : `organisation.v1` ne publie pas de port de lecture du fuseau (décision (4)) |

Références NON SQL aux types historiques (entités JPA `PatientJpaEntity`, `AssureJpaEntity`, `AssurePatientJpaEntity`, `PatientRepositoryAdapter`, `PatientMapper`, `bc-patient` `Patient`/`FinOccupation`…, utilisées par la Séance, l'export FHIR, le cahier de dialyse, KDIGO, `SaisieInfirmierNotifier`, `DossierMedicalPatientRepositoryAdapter`) : **JALON** — le modèle patient historique reste le modèle de lecture de ces contextes ; retrait de `bc-patient` et des entités avec la reprise de ces contextes.

## Plan en sessions

| Session | Contenu | État |
|---|---|---|
| S1 (2026-11-04) | inventaire, trois ports publiés, trois adapters, contrats double / H2 / PG16 (14 + 14 + 14) | faite |
| S2 (2026-11-04) | six lecteurs migrés (#1 à #3, #6 partie PEC, #7, #9) : ACL minces `PatientsDuCentreDeLaFiche`, `NomsDesPatientsDeLaFiche`, `ForfaitsDesPatientsDeLaCouverture` (Absences), `SeancesFacturablesJdbc` (partie PEC), `SeanceBillingEligibilityParLaCouverture` (Séance), `DashboardRestController` ; dépendances Maven Absences et Facturation vers le module (sans cycle) ; `TableDuPatientCouvertureTest` RÉACTIVÉ (5 tests, 18 jalons datés avec raison et échéance, échec sur entrée morte) ; seams inscrits avant édition puis fermés ; build complet vert, AUCUN golden modifié ; tests des ACL migrés conservés au-dessus des adapters RÉELS (parité de bout en bout) | faite |
| S3 (2026-11-04) | preuves : `./mvnw -o test -fae` complet vert, `check-arch` 0 violation, ArchUnit (dans le build), PIT du code nouveau 98 % (`pit-pc5-module.sh`, 53 mutants tués sur 54), rejeu PG16 complet (contrat du langage publié 14 tests + dépôts 27 tests, et PC4) ; docs : ADR 0079, KB-PAT-49 (dette), glossaire, §44 (parité des compteurs, SELECT seuls), roadmap, `GEL.md` (clôture du module), documentation client régénérée ; aucune règle de gestion nouvelle (refactoring à comportement identique), RG et annexe inchangés | faite |
