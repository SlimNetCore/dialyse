-- V11: Relational insured history (assure + assure_patient)

CREATE TABLE IF NOT EXISTS assure (
    numero_assurance VARCHAR(100) PRIMARY KEY,
    center_id UUID NOT NULL,
    nom VARCHAR(255),
    prenom VARCHAR(255),
    sexe VARCHAR(10),
    date_naissance DATE,
    tel_personnel VARCHAR(50),
    tel_mobile VARCHAR(50),
    tel_bureau VARCHAR(50),
    adresse VARCHAR(500),
    groupe_sanguin VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_assure_center ON assure(center_id);
CREATE INDEX IF NOT EXISTS idx_assure_nom_prenom ON assure(nom, prenom);

CREATE TABLE IF NOT EXISTS assure_patient (
    patient_id UUID NOT NULL,
    numero_assurance VARCHAR(100) NOT NULL,
    center_id UUID NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    date_affectation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (patient_id, numero_assurance)
);

ALTER TABLE patients ADD COLUMN IF NOT EXISTS assure_numero_assurance VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_assure_patient_patient ON assure_patient(patient_id, center_id);
CREATE INDEX IF NOT EXISTS idx_assure_patient_primary ON assure_patient(patient_id, center_id, is_primary);


