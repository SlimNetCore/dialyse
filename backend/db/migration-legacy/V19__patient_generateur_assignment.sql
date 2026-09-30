-- V19: Generateur assignment for patients
-- Link a patient to a dialysis generator (generateur de dialyse)
-- This allows automatic routing of generator telemetry data to the correct patient session

-- Enrich generateur table with optional marque/modele fields
ALTER TABLE generateur
    ADD COLUMN IF NOT EXISTS marque VARCHAR(100);
ALTER TABLE generateur
    ADD COLUMN IF NOT EXISTS modele VARCHAR(100);
ALTER TABLE generateur
    ADD COLUMN IF NOT EXISTS etat VARCHAR(30) DEFAULT 'FONCTIONNEL';

-- Add generateur_id FK column on patients table
ALTER TABLE patients
    ADD COLUMN IF NOT EXISTS generateur_id UUID;

-- Index for quick lookups (e.g. "which patient is on generator X?")
CREATE INDEX IF NOT EXISTS idx_patients_generateur_id ON patients (generateur_id);
CREATE INDEX IF NOT EXISTS idx_patients_center_generateur ON patients (center_id, generateur_id);

-- Données de démo ajoutées dans `db/seed.sql` pour garder cette migration compatible H2/PostgreSQL.
-- Cette migration ne fait que préparer le schéma (colonnes + index).



