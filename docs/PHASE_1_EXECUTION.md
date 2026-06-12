# Phase 1 - Execution log (Cahier dialyse)

## Scope implemented

This first increment focuses on **database and data-model alignment** from `docs/plan_cahier_dialyse_agent.md`.

### Delivered in this increment

- Added migration `backend/src/main/resources/db/migration/V15__cahier_dialyse_phase1_alignment.sql`
- Aligned SQL column types for time fields:
    - `volet_paramedical.heure_arret_heparine`
    - `releves_per_seance.heure_releve`
    - `medicaments_seance.heure_injection`
- Aligned user identifier types to UUID:
    - `volet_paramedical.signature_infirmier_id`
    - `prescriptions_medicales.medecin_id`
- Aligned precision/contracts:
    - `articles_consommes_seance.quantite` to `DECIMAL(8,2)`
    - `volet_paramedical.uf_cible_ml`, `uf_reelle_ml`, `volume_rincage_ml` to `SMALLINT`
- Added missing FK constraints:
    - nurse signature -> `app_user(id)`
    - prescribing doctor -> `app_user(id)`
    - consumed article movement -> `stock_mouvements(id)`
- Added enum-like CHECK constraints for key controlled fields.
- Added patient timeline indexes for abords, prescriptions, analyses.

### Sub-lot 2 delivered

- Domain model typing aligned with SQL contract:
    - `VoletParamedical.heureArretHeparine` -> `LocalTime`
    - `VoletParamedical.signatureInfirmierId` -> `UUID`
    - `RelevePerSeance.heureReleve` -> `LocalTime`
    - `MedicamentSeance.heureInjection` -> `LocalTime`
- Corrected naming consistency in `RelevePerSeance` accessors:
    - `getPressionVeineuse` / `setPressionVeineuse`
    - `getPressionArterielle` / `setPressionArterielle`
- Implemented missing domain classes to complete Phase 1 model baseline:
    - `AbordVasculaire`
    - `PrescriptionMedicale`

### Sub-lot 3 delivered

- Added domain repository ports:
    - `AbordVasculaireRepositoryPort`
    - `PrescriptionMedicaleRepositoryPort`
- Added JPA entities:
    - `AbordVasculaireJpaEntity` (table `abords_vasculaires`)
    - `PrescriptionMedicaleJpaEntity` (table `prescriptions_medicales`)
- Added Spring Data repositories with patient timeline ordering:
    - `findByPatientIdAndCenterIdOrderByDateCreationDesc`
    - `findByPatientIdAndCenterIdOrderByDatePrescriptionDesc`
- Added persistence adapters:
    - `AbordVasculaireRepositoryAdapter`
    - `PrescriptionMedicaleRepositoryAdapter`

### Sub-lot 4 delivered

- Added Step 3 domain use-cases:
    - `AbordVasculaireUseCase`
    - `PrescriptionMedicaleUseCase`
- Added application services:
    - `AbordVasculaireDomainService`
    - `PrescriptionMedicaleDomainService`
- Added unit tests for service behavior and defaults:
    - `AbordVasculaireDomainServiceTest`
    - `PrescriptionMedicaleDomainServiceTest`

### Sub-lot 5 delivered

- Added request DTOs:
    - `UpsertAbordVasculaireRequest`
    - `UpsertPrescriptionMedicaleRequest`
- Added Step 3 REST controllers:
    - `AbordVasculaireRestController`
    - `PrescriptionMedicaleRestController`
- Implemented endpoints:
    - `GET /api/v1/patients/{patientId}/abords-vasculaires`
    - `POST /api/v1/patients/{patientId}/abords-vasculaires`
    - `PUT /api/v1/patients/{patientId}/abords-vasculaires/{abordId}`
    - `GET /api/v1/patients/{patientId}/prescriptions`
    - `POST /api/v1/patients/{patientId}/prescriptions`
    - `PUT /api/v1/patients/{patientId}/prescriptions/{prescriptionId}`
- Added controller tests:
    - `AbordVasculaireRestControllerTest`
    - `PrescriptionMedicaleRestControllerTest`

### Sub-lot 6 delivered

- Added dossier-medical domain ports/services:
    - `DossierMedicalPatientRepositoryPort`
    - `DossierMedicalPatientUseCase`
    - `DossierMedicalPatientDomainService`
- Added persistence layer for `dossier_medical_patient`:
    - `DossierMedicalPatientJpaEntity`
    - `DossierMedicalPatientJpaRepository`
    - `DossierMedicalPatientRepositoryAdapter`
- Added API endpoint contract for dossier médical:
    - `GET /api/v1/patients/{patientId}/dossier-medical`
    - `POST /api/v1/patients/{patientId}/dossier-medical`
    - `PUT /api/v1/patients/{patientId}/dossier-medical`
- Added request DTO and tests:
    - `UpsertDossierMedicalPatientRequest`
    - `DossierMedicalPatientDomainServiceTest`
    - `DossierMedicalPatientRestControllerTest`

### Sub-lot 7 delivered

- Completed prescriptions contract:
    - `GET /api/v1/patients/{patientId}/prescriptions?from=&to=`
    - `DELETE /api/v1/patients/{patientId}/prescriptions/{prescriptionId}`
- Added full analyses backend flow:
    - domain model: `ResultatAnalyse`
    - ports/use-case/service: `ResultatAnalyseRepositoryPort`, `ResultatAnalyseUseCase`, `ResultatAnalyseDomainService`
    - persistence: `ResultatAnalyseJpaEntity`, `ResultatAnalyseJpaRepository`, `ResultatAnalyseRepositoryAdapter`
    - request DTO: `UpsertResultatAnalyseRequest`
    - controller: `ResultatAnalyseRestController`
- Implemented analyses endpoints:
    - `GET /api/v1/patients/{patientId}/analyses?from=&to=`
    - `POST /api/v1/patients/{patientId}/analyses`
    - `PUT /api/v1/patients/{patientId}/analyses/{analyseId}`
    - `DELETE /api/v1/patients/{patientId}/analyses/{analyseId}`
- Added/updated tests:
    - `PrescriptionMedicaleDomainServiceTest`
    - `PrescriptionMedicaleRestControllerTest`
    - `ResultatAnalyseDomainServiceTest`
    - `ResultatAnalyseRestControllerTest`

### Sub-lot 8 delivered

- Started Phase 2.7 stats backend with a dedicated query service:
    - `PatientStatsQueryService`
- Added stats REST controller:
    - `PatientStatsRestController`
- Implemented endpoints:
    - `GET /api/v1/patients/{patientId}/stats/paramedical`
    - `GET /api/v1/patients/{patientId}/stats/medical`
    - `GET /api/v1/patients/{patientId}/stats/export?format=csv|pdf`
- Current export behavior:
    - CSV implemented
    - PDF returns `501 NOT_IMPLEMENTED` (placeholder for next sub-lot)
- Added controller tests:
    - `PatientStatsRestControllerTest`

### Sub-lot 9 delivered

- Added frontend Step 4 statistics page:
    - `frontend/src/app/features/patient/patient-stats.component.ts`
- Added patient stats API contract and methods:
    - `getPatientParamedicalStats`
    - `getPatientMedicalStats`
    - `exportPatientStats`
- Added typed frontend models:
    - `PatientParamedicalStats`
    - `PatientMedicalStats`
- Added route integration:
    - `frontend/src/app/features/patient/patient.routes.ts` -> `:id/stats`
- Added list-to-stats navigation actions:
    - `frontend/src/app/features/patient/patient-list.component.ts` (desktop + mobile actions)
    - `frontend/src/app/features/patient/patient-dashboard.component.ts`
- Added i18n labels:
    - `frontend/public/i18n/fr.json`
    - `frontend/public/i18n/en.json`
    - `frontend/public/i18n/ar.json`
    - `frontend/public/i18n/kab.json`
- Build validation:
    - `npm run build` passed (only existing style budget warnings)

## Notes

- Current migration style in the project is PostgreSQL-oriented (`DO $$`, catalog checks). The new migration follows the
  same convention.
- This increment does **not** yet enforce API-side validation rules; those are planned in later phases.

## Next phase candidates

1. Implement PDF export for `stats/export?format=pdf`.
2. Add integration tests for stats SQL query service.
3. Add frontend charts and role-aware export UX refinements for Step 4.
