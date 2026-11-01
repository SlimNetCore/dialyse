---
slice: patient-couverture-pc1-identite-mouvements
status: characterized
date: 2026-11-01
sources: [.ddd/migration/slices/patient-couverture-pc1-identite-mouvements.md, .ddd/tactical/patient-couverture.md, ADR 0004, 0024, 0064]
---

# Filet de caractérisation — PC1 Identité administrative et mouvements du patient

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `CenterAccessGuard`/`RoleScopeFilter`, H2 du profil
`test`) et état SQL des tables `patients`, `mouvement_patient`. Aucun mock du code caractérisé. Les lectures/écritures
patient passent par `PatientRestController`, `MouvementPatientRestController`, `PatientApplicationService` et
`MouvementPatientQueryService`, avec isolation multi-centre par `center_id`.

## Tests existants couvrant la slice

- `backend/platform/src/test/java/.../infrastructure/web/rest/PatientEtatMouvementsIntegrationTest` (états et mouvements
  patient : admission, sortie, décès, greffe, guérison, transfert, invariant date de sortie requise, isolation centre) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/MouvementsPatientsIntegrationTest` (libération des places,
  historique des mouvements, idempotence, pagination, scoping centre) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/MouvementPatientRestControllerTest` (contrôleur REST des
  mouvements, rôles, pagination) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/PatientFicheEcritureIntegrationTest` (écriture de la fiche
  patient, admission) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/PatientRestControllerSummaryTest` (résumé patient,
  multi-centre) ;
- `backend/platform/src/test/java/.../infrastructure/persistence/adapter/PatientRepositoryAdapterTest` et
  `PatientRepositoryAdapterCacheIntegrationTest` (adaptateur JPA, cache par `center_id`) ;
- `backend/bc-patient/src/test/java/.../domain/patient/service/MouvementsPatientTest` (logique domaine des mouvements) ;
- `backend/bc-patient/src/test/java/.../domain/patient/model/PatientContactValidationTest` (validation contact patient).

## Scénarios verrouillés (multi-centre)

- Création/admission d'un patient rattaché à un `center_id` ; un patient d'un centre A n'apparaît pas dans le centre B.
- Mouvements (admission, transfert, sortie, décès, greffe, guérison) enregistrés dans `mouvement_patient` avec
  `center_id`.
- Invariant R2 : état de sortie exige une date d'événement (`date_evenement_etat`) ; refus sans date.
- Libération des places : seules les places échues du centre concerné sont libérées ; idempotence ; isolation
  inter-centres.
- Historique des mouvements : pagination `page`/`size` avec enveloppe `{items,total,page,size}` ; filtrage par type et
  dates.
- Cache patient (`ref.patient.detail`, `ref.liste.patients`) invalidé par `center_id` ; pas de fuite inter-centres.
- `centerId` systématiquement propagé dans `PatientRepositoryPort` / `MouvementPatientRepositoryPort`.

## Stabilité

Suite exécutée sur H2 (profil `test`). Isolation centre vérifiée par `MouvementsPatientsIntegrationTest` et
`PatientEtatMouvementsIntegrationTest`. Aucun mock du code caractérisé.

## Points d'attention

- Les données de démo (seed) peuvent pré-peupler des patients ; les tests nettoient par `center_id`.
- Le champ « place » et « mouvement place libérée » quitte ce contexte vers Planification (ADR 0004) ; le filet couvre
  la
  frontière actuelle.
- Les règles de gestion associées : RG-PAT-0xx (voir `docs/reference/regles-de-gestion/04-patient.md`).
- Compatibilité H2 (dev) / PostgreSQL (prod) : aucun SQL spécifique à un moteur.