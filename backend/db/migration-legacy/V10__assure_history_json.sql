-- V10: Assure history stored as JSON text on the patient record
ALTER TABLE patients ADD COLUMN IF NOT EXISTS assure_history_json TEXT;

