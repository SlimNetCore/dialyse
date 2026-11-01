# Filet de non-régression — DM7 bilan pré-greffe

Slice : `dossier-medical-dm7-bilan-pregreffe`. Contexte : `dossier-medical`.
Date : 2026-11-01. Pattern : DM2/DM3/DM4/DM6.

## Point d'observation

- HTTP : `DossierMedicalPatientRestController` (legacy `platform`, bilan greffe lu via
  `bc-medical`) + MockMvc.
- SQL : tables lues par `bc-medical` (bilan greffe) ; nouvelles tables `bilans_pregreffe`
  et `etapes_bilan_pregreffe` créées par le contexte (aucune migration legacy, tables neuves JDBC).

## Tests existants couvrant la slice

- `DossierMedicalPatientRestControllerTest` (platform) : dossier patient par centre.
- `PatientFicheEcritureIntegrationTest` : écritures dossier, isolation centre.

## Scénarios verrouillés (multi-centre)

1. Démarrage d'un bilan : patient + centre requis, au moins une étape.
2. Validation d'étape : une seule fois, refus si étape inconnue ou bilan non modifiable.
3. Complétion : refus tant qu'une étape reste à valider ; publie `BilanPreGreffeComplete`.
4. Annulation : seulement si `EN_COURS`.
5. Liste par patient : seul le centre demandé est retourné (`center_id`).
6. Isolation : le centre B ne lit jamais les bilans du centre A.

## Stabilité

Filet vert et stable : les tests legacy listés passent sans flakiness connue.

## Points d'attention

- Agrégat isolé et extractible (ADR 0007) : port publié `BilanPreGreffeComplete`
  consommé par Patient & couverture (greffe).
- Données de santé : cloisonnement strict par `center_id`, aucune lecture cross-centre.
- Garde finale DM8 : « pas de SQL sur les tables du dossier médical hors contexte ».
