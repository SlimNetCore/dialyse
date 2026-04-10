-- Add missing insured phone fields persisted by domain model
ALTER TABLE patients ADD COLUMN IF NOT EXISTS assure_tel_mobile VARCHAR(30);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS assure_tel_bureau VARCHAR(30);

-- Helpful indexes for consultation/update performance
CREATE INDEX IF NOT EXISTS idx_attestation_patient_center_created
  ON attestation_droit(center_id, patient_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pec_patient_center_created
  ON prise_en_charge(center_id, patient_id, created_at DESC);

