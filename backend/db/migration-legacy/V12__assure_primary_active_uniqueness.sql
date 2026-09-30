-- V12: Ensure a single active insured assignment per patient

ALTER TABLE assure_patient
    ADD COLUMN IF NOT EXISTS id UUID DEFAULT RANDOM_UUID();

ALTER TABLE assure_patient
    ADD COLUMN IF NOT EXISTS date_debut_affectation DATE;

ALTER TABLE assure_patient
    ADD COLUMN IF NOT EXISTS date_fin_affectation DATE;

-- Keep legacy rows consistent with "active = date_fin_affectation IS NULL"
UPDATE assure_patient
SET date_debut_affectation = COALESCE(date_debut_affectation, CAST(date_affectation AS DATE))
WHERE date_debut_affectation IS NULL;

-- Unique active assignment per patient and center
CREATE UNIQUE INDEX IF NOT EXISTS uq_assure_patient_active_assignment
    ON assure_patient (patient_id, center_id) WHERE date_fin_affectation IS NULL;

