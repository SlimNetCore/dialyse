ALTER TABLE patients
    ADD COLUMN IF NOT EXISTS type_patient VARCHAR(30) NOT NULL DEFAULT 'NON_VACANCIER';

CREATE INDEX IF NOT EXISTS idx_patients_center_type
    ON patients (center_id, type_patient);

