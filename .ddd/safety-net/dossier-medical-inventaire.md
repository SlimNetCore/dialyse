---
status: proposed
date: 2026-11-06
sources: [scan par grep/query sur backend/platform/src/main, GEL.md (ADR 0072), .ddd/migration/slices/dossier-medical-*.md]
---

# Inventaire du legacy médical et découpage DM1..DM8 (refactoring sur place, ADR 0024)

Le legacy médical est dans `platform` (contrôleurs `infrastructure/web/rest`, entités `infrastructure/persistence/entity`) et `bc-medical` (domaine). Le module `contexts/dossier-medical` est gelé (GEL.md) et n'est pas la référence.

## Routes (16 contrôleurs médicaux + 2 écrivains de la Séance) et découpage réel

| Slice | Contrôleurs et routes (`/api/v1/patients/{id}/...` sauf mention) | Rôles (`@PreAuthorize`) | Tables |
|---|---|---|---|
| **DM1 Constantes** | `GET constantes` (`ConstantesRestController`, lecture seule) ; écrivain = `PUT /seances/{id}/volet-paramedical` (`VoletParamedicalRestController`, propriété Séance) ; effacement = `DELETE /seances/{id}` (`SeanceSuppressionRestController`) | lecture MEDECIN, ADMIN ; écriture ADMIN, INFIRMIER, SECRETAIRE | `volet_paramedical`, `seances` |
| **DM2 Prescriptions** | `ordonnances` (GET, POST, signer, imprimer, annuler) ; `prescriptions` (GET, active, search, POST, PUT, DELETE) | lecture MEDECIN, ADMIN (+ INFIRMIER pour `prescriptions/active`) ; écriture MEDECIN | `ordonnances`, `lignes_ordonnance`, `prescriptions_medicales`, état `ordonnance.jrxml` |
| **DM3 Examens** | `demandes-examen` (GET, POST, preleve, resultat-disponible, valider, annuler) | lecture MEDECIN, ADMIN ; écriture MEDECIN | `demandes_examen`, `lignes_demande_examen` |
| **DM4 Sérologie** | `serologies` (GET, synthese, POST, PUT, DELETE) ; audit `SEROLOGIE_*` ; consommée par Planification P5 | lecture MEDECIN, ADMIN ; écriture MEDECIN | `serologies_patient` |
| **DM5 Antécédents et allergies** | `antecedents` (GET, POST, PUT, resoudre, DELETE) ; `allergies` (GET, critiques, POST, PUT, DELETE) | lecture MEDECIN, ADMIN ; écriture MEDECIN | `antecedents_medicaux`, `allergies_patient` |
| **DM6 Biologie** | `observations` (+ `demandes-examen/{id}/observations`) ; `analyses` (GET, `analyses/search`, POST, PUT, DELETE) | lecture MEDECIN, ADMIN ; écriture MEDECIN | `observations_biologiques`, `resultats_analyses` |
| **DM7 Bilan pré-greffe** | `greffe/bilan` (+ statut, immunologique, notes, decisions-rcp) ; `greffe/etapes` ; `greffe/donneurs` ; `greffe/kdigo` ; `greffe/export-pdf` | MEDECIN (+ INFIRMIER sur les étapes) | `bilans_pre_greffe`, `etapes_bilan_pre_greffe`, `donneurs_vivants`, décisions RCP |
| **DM8 Lectures publiées / transverse** | `dossier-medical` (GET, POST, PUT ; seule lecture auditée) ; `dossier-medical/export-fhir` ; `stats/medical`, `stats/export` ; lecteurs Direction et Planification | voir plus bas | `dossier_medical_patient`, `volet_medical` |

### Corrections proposées au découpage (trous du plan)
1. **Anémie et observance, absents de DM1..DM8** : `suivi-anemie`, `observance-anemie`, `alertes-observance` (+ `resoudre`), `administrations-anemie` (GET, POST), planificateur `ObservancePrescriptionScheduler` (cron 06:30), tables `administrations_anemie`, `alertes_observance`. Proposition : les rattacher à **DM2** (ils dérivent des prescriptions) ; à décider.
2. **Abords vasculaires** (`abords-vasculaires` GET, POST, PUT, DELETE, table `abords_vasculaires`) : non affectés ; exportés en FHIR (`Procedure`). Proposition : **DM5**.
3. **Volet médical de la séance** (`PUT /seances/{id}/volet-medical`, table `volet_medical`) : écrit dans la Séance ; proposition : reste Séance (SE2), lu par DM8.
4. **DM1 est plus petit que décrit** : le tactique prévoit `EnregistrerConstante`/`ConsulterConstantesDuPatient` ; le legacy n'a que la consultation. Il n'existe ni création, ni modification, ni suppression de « constante ». Le filet couvre donc la lecture et ses écrivains (volet paramédical, suppression de séance), sans en prendre la propriété.

## DM1 : qui écrit, qui lit les constantes

| Rôle | Acteur | Fichier | Observé par |
|---|---|---|---|
| Écrivain unique | `PUT /seances/{id}/volet-paramedical` | `VoletParamedicalRestController`, `VoletParamedicalDomainService` (bornes poids et TA), `VoletParamedicalRepositoryAdapter` | DM1 (ce filet, effet sur les constantes) ; propriété SE2 |
| Effaceur | `DELETE /seances/{id}` | `SeanceSuppressionRestController`, `SuppressionSeanceService` | DM1 (effet) |
| Effaceur technique | rollback d'import | `MigrationRollbackJdbcAdapter` | hors filet |
| Lecteur DM1 | `GET /patients/{id}/constantes` | `ConstantesRestController`, `ConstantesQueryService`, `ConstanteSeanceResponse` | DM1 |
| Lecteur | `GET /seances/{id}` (clé `paramedical`) | `SeanceRestController` | DM1 (sans garde de centre) |
| Lecteur | `GET /seances/station...` (récap) | `SeanceStationRestController` | SE (hors DM1) |
| Lecteur | `GET /patients/{id}/stats/paramedical` et `search`, `stats/export` | `PatientStatsQueryService` (jointure `seances` x `volet_paramedical`) | DM1 (stats/paramedical) |
| Lecteur | état « cahier de dialyse » | `reports/cahier_dialyse.jrxml` (`CahierDialyseRestController`) | test existant `CahierDialyseReportIntegrationTest`, hors DM1 |
| Non lecteurs | FHIR (`FhirExportService`) et KDIGO : ne lisent PAS les constantes (FHIR : Patient, Condition, AllergyIntolerance, Observation depuis analyses, MedicationRequest, Procedure) | | constat de lecture du code |

## Lecteurs hors module des données médicales (tables lues hors contrôleurs médicaux)
- Direction : `DirectionIndicatorsQueryService` (`resultats_analyses`, `serologies_patient`), `DirectionBreakdownQueryService`.
- Planification (module) : `RisquesInfectieuxJdbc` (ACL `legacydossiermedical`, sérologie), `ContraintesDesPatientsJdbc`.
- Statistiques : `PatientStatsQueryService`, `SuiviAnemieQueryService`, `ObservanceAnemieQueryService`.
- Import et purge : `HistoricalRecordJdbcAdapter`, `MigrationRollbackJdbcAdapter`, `ReferentialAdminTables`, `db/demo/renadial-purge.sql`.
- États : `reports/ordonnance.jrxml`, `reports/cahier_dialyse.jrxml`.
- Planificateur : `ObservancePrescriptionScheduler`, `ObservanceMesure`.
- Le module gelé `contexts/dossier-medical` (JDBC sur `demandes_examen`, `observations_biologiques`, `bilans_pre_greffe`) : non branché.

## Audit (résolveur `AuditActionResolver`)
Toute écriture `/api/v1/**` est tracée (`<RESSOURCE>_<VERBE>`) ; seule lecture tracée : `GET /patients/{id}/dossier-medical`. Les lectures de constantes, ordonnances, sérologie, antécédents, allergies, biologie, FHIR et KDIGO ne le sont pas.

## Questions ouvertes
- Rattachement de l'anémie/observance, des abords vasculaires, des donneurs vivants (DM7 proposé) et du volet médical.
- Propriété du `PUT volet-paramedical` entre DM1 et SE2 : ce filet observe l'effet sur les constantes ; SE2 devra y ajouter ses règles (signature, facturation).
