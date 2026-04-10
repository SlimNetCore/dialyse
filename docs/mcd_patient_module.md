# MCD - Module Patient (multi-centre)

## Entites principales

- `center`
  - `id` (PK)
  - `code`, `name`

- `patients`
  - `id` (PK)
  - `center_id` (FK -> center.id)
  - donnees identite / contact / affectation
  - donnees assurance patient + assure actif (snapshot)
  - `numero_assurance` (unique par centre)

- `attestation_droit`
  - `id` (PK)
  - `center_id` (FK -> center.id)
  - `patient_id` (FK -> patients.id)
  - `date_debut`, `date_fin`
  - `created_at`

- `prise_en_charge`
  - `id` (PK)
  - `center_id` (FK -> center.id)
  - `patient_id` (FK -> patients.id)
  - demande: dates + forfait
  - accord: dates + forfait
  - `statut` (`CREE`, `VALIDEE`, `CLOTUREE`)
  - `created_at`

## Relations

- `center 1..n patients`
- `patients 1..n attestation_droit`
- `patients 1..n prise_en_charge`

## Regles de gestion

- Isolation multi-centre obligatoire (`center_id` dans toutes les tables metier)
- Patient vacancier local/etranger:
  - attestation non obligatoire
- Patient non vacancier:
  - attestation obligatoire
- Seance autorisee uniquement si PEC `VALIDEE`
- Creation patient possible sans PEC (patient non facturable)

## Ajustements recents

- Ajout des champs assures manquants dans `patients`:
  - `assure_tel_mobile`
  - `assure_tel_bureau`
- Index de performance:
  - `idx_attestation_patient_center_created`
  - `idx_pec_patient_center_created`

