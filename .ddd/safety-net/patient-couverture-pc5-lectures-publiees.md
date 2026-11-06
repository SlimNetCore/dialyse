---
slice: patient-couverture-pc5-lectures-publiees
status: obsolete (note du 2026-11-01 ; PC5 est `built`, voir la fiche, l'ADR 0079 et la liste blanche datée de TableDuPatientCouvertureTest)
date: 2026-11-01
sources: [.ddd/migration/slices/patient-couverture-pc5-lectures-publiees.md, .ddd/tactical/patient-couverture.md, ADR 0064]
---

# Filet de caractérisation — PC5 Lectures publiées et retrait du legacy Patient & couverture

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `CenterAccessGuard`/`RoleScopeFilter`, H2 du profil
`test`) et état SQL des tables de Patient & couverture (`patients`, `mouvement_patient`, `assure`, `assure_patient`,
`pec`, `attestation_droit`). Aucun mock du code caractérisé. Tous les consommateurs aval (Facturation,
Comptabilité, Séance, Planification, Absences patients, Dossier médical, Pilotage) lisent Patient & couverture via
`patientcouverture.v1` et les ports publiés (`PatientRepository`, `AssureRepository`, `PECRepository`,
`AttestationRepository`, `MouvementPatientRepository`) ; aucune requête SQL directe sur les tables de Patient &
couverture hors du contexte `patient-couverture`.

## Tests existants couvrant la slice

- `backend/platform/src/test/java/.../characterization/patientcouverture/AssurePrincipalCharacterizationIntegrationTest`
  (assuré principal, affectation, historique, isolation centre) ;
- `backend/platform/src/test/java/.../characterization/patientcouverture/PecCharacterizationIntegrationTest` (cycle PEC,
  validation, clôture, isolation centre) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/PatientEtatMouvementsIntegrationTest` et
  `MouvementsPatientsIntegrationTest` (identité, mouvements, pagination, scoping centre) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/PatientRestControllerSummaryTest` (résumé patient,
  multi-centre) ;
- `backend/platform/src/test/java/.../architecture/TableDeLOrganisationTest` (modèle de garde « pas de SQL hors
  contexte ») ;
- Tests domaine `backend/contexts/patient-couverture/src/test/java/.../domain/{Patient,Assure,Pec}Test`.

## Scénarios verrouillés (multi-centre)

- Liste/détail patient filtré par `centerId` de session ; un centre A ne voit pas les patients du centre B.
- Mouvements (admission, transfert, sortie, décès, greffe, guérison) enregistrés avec `center_id` ; historique paginé
  `{items,total,page,size}`.
- Assuré principal : un seul par patient (R1) ; affectation et historique isolés par centre.
- PEC : cycle `CREE -> VALIDEE -> CLOTUREE` (R3) ; attestation liée à une PEC active (R5) ; isolation centre.
- Attestations de droits : dates obligatoires, date fin ≥ date début ; liste par patient filtrée par `centerId`.
- Caches `ref.patient.detail`, `ref.pec.active`, `ref.liste.patients` : clé incluant toujours le `centerId` ; éviction
  synchrone sur écriture ; pas de fuite inter-centres.
- Aucun SQL direct sur `patients`, `mouvement_patient`, `assure`, `assure_patient`, `pec`, `attestation_droit` hors du
  contexte `patient-couverture` (garde « pas de SQL sur les tables Patient & couverture hors contexte »).

## Stabilité

Suite exécutée sur H2 (profil `test`). Isolation centre vérifiée par les tests d'intégration patient/PEC/assuré.
Aucun mock du code caractérisé.

## Points d'attention

- Les données de démo (seed) peuvent pré-peupler des patients ; les tests nettoient par `center_id`.
- Le retrait effectif de `bc-patient`/`bc-assure`/`bc-insurance`/`bc-pec` suppose que tous les SQL hors contexte aient
  été remplacés par les ports publiés ; la garde « pas de SQL sur les tables Patient & couverture hors contexte » doit
  rester verte.
- Les règles de gestion associées : RG-PAT-0xx (voir `docs/reference/regles-de-gestion/04-patient.md`).
- Compatibilité H2 (dev) / PostgreSQL (prod) : aucun SQL spécifique à un moteur.
- Tout nouveau code d'erreur métier est cité dans le référentiel `docs/reference/regles-de-gestion/`.