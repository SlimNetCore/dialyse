-- V6: Forfaits table, PEC extended columns, patient EPO/FER columns

-- Forfait table
CREATE TABLE IF NOT EXISTS forfait (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL REFERENCES centers(id),
    code VARCHAR(20) NOT NULL,
    nom VARCHAR(200) NOT NULL,
    prix DECIMAL(12,2) DEFAULT 0,
    nombre_seances INT DEFAULT 0
);

-- Extend PEC
ALTER TABLE prise_en_charge ADD COLUMN IF NOT EXISTS forfait_demande_id UUID;
ALTER TABLE prise_en_charge ADD COLUMN IF NOT EXISTS date_debut_effectif DATE;
ALTER TABLE prise_en_charge ADD COLUMN IF NOT EXISTS date_fin_effectif DATE;
ALTER TABLE prise_en_charge ADD COLUMN IF NOT EXISTS forfait_effectif_id UUID;

-- Patient EPO/FER
ALTER TABLE patients ADD COLUMN IF NOT EXISTS epo_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS epo_date DATE;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS fer_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS fer_date DATE;

-- Forfait seed data
MERGE INTO forfait (id, center_id, code, nom, prix, nombre_seances) KEY (id)
VALUES ('f0000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'F-HD-12', 'Forfait HD 12 séances', 120000.00, 12);
MERGE INTO forfait (id, center_id, code, nom, prix, nombre_seances) KEY (id)
VALUES ('f0000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'F-HD-13', 'Forfait HD 13 séances', 130000.00, 13);
MERGE INTO forfait (id, center_id, code, nom, prix, nombre_seances) KEY (id)
VALUES ('f0000001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'F-HD-14', 'Forfait HD 14 séances', 140000.00, 14);

