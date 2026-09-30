-- V2: create patient and related MVP tables (multi-centre aware)

CREATE TABLE IF NOT EXISTS patients (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    patient_code VARCHAR(100) NULL,
    civilite VARCHAR(10),
    nombre_enfants INT,
    sexe VARCHAR(10) NOT NULL,
    groupe_sanguin VARCHAR(10),
    nom VARCHAR(255) NOT NULL,
    prenom VARCHAR(255) NOT NULL,
    date_admission DATE NOT NULL,
    date_naissance DATE,
    lieu_naissance VARCHAR(255),
    situation_familiale VARCHAR(100),
    profession VARCHAR(255),
    tel_mobile VARCHAR(50),
    email VARCHAR(255),
    adresse TEXT,
    premiere_seance_epo DATE,
    premiere_seance_fer_injectable DATE,
    observation TEXT,
    numero_assurance VARCHAR(100) NOT NULL,
    medecin_traitant_id UUID,
    position_creneau VARCHAR(100),
    transporteur_id UUID,
    salle_id UUID,
    generateur_id UUID,
    sous_kt BOOLEAN DEFAULT FALSE,
    categorie_transport VARCHAR(100),
    etat_patient VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_patients_center FOREIGN KEY (center_id) REFERENCES centers(id) ON DELETE CASCADE,
    CONSTRAINT uk_center_numassurance UNIQUE (center_id, numero_assurance)
);

CREATE INDEX IF NOT EXISTS idx_patients_center_created ON patients (center_id, created_at);
CREATE INDEX IF NOT EXISTS idx_patients_center_etat ON patients (center_id, etat_patient);

-- Prise en charge table (minimal)
CREATE TABLE IF NOT EXISTS prise_en_charge (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    center_id UUID NOT NULL,
    date_debut_demande DATE,
    date_fin_demande DATE,
    forfait_demande VARCHAR(100),
    date_debut_effectif DATE,
    date_fin_effectif DATE,
    forfait_effectif VARCHAR(100),
    statut VARCHAR(20) NOT NULL DEFAULT 'CREE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pec_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE,
    CONSTRAINT fk_pec_center FOREIGN KEY (center_id) REFERENCES centers(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pec_center_patient ON prise_en_charge (center_id, patient_id);

-- Attestation table (minimal)
CREATE TABLE IF NOT EXISTS attestation_droit (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    center_id UUID NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_attestation_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE,
    CONSTRAINT fk_attestation_center FOREIGN KEY (center_id) REFERENCES centers(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_attestation_center ON attestation_droit (center_id, date_debut, date_fin);

