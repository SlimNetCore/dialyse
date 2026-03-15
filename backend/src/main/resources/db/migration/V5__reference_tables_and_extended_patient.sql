-- V5: Reference tables for dropdowns + extended patient fields

-- ═══ REFERENCE: Caisse d'assurance ═══
CREATE TABLE IF NOT EXISTS caisse_assurance (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL REFERENCES centers(id),
    code VARCHAR(20) NOT NULL,
    nom VARCHAR(200) NOT NULL,
    type_caisse VARCHAR(30) DEFAULT 'STANDARD'
);

-- ═══ REFERENCE: Agence ═══
CREATE TABLE IF NOT EXISTS agence (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL REFERENCES centers(id),
    caisse_id UUID NOT NULL REFERENCES caisse_assurance(id),
    code VARCHAR(20) NOT NULL,
    nom VARCHAR(200) NOT NULL
);

-- ═══ REFERENCE: Centre payeur ═══
CREATE TABLE IF NOT EXISTS centre_payeur (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL REFERENCES centers(id),
    agence_id UUID NOT NULL REFERENCES agence(id),
    code VARCHAR(20) NOT NULL,
    nom VARCHAR(200) NOT NULL,
    adresse TEXT
);

-- ═══ REFERENCE: Medecin ═══
CREATE TABLE IF NOT EXISTS medecin (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL REFERENCES centers(id),
    nom VARCHAR(200) NOT NULL,
    prenom VARCHAR(200),
    specialite VARCHAR(100)
);

-- ═══ REFERENCE: Salle ═══
CREATE TABLE IF NOT EXISTS salle (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL REFERENCES centers(id),
    code VARCHAR(20) NOT NULL,
    nom VARCHAR(200) NOT NULL
);

-- ═══ REFERENCE: Generateur ═══
CREATE TABLE IF NOT EXISTS generateur (
    id UUID PRIMARY KEY,
    salle_id UUID NOT NULL REFERENCES salle(id),
    center_id UUID NOT NULL REFERENCES centers(id),
    numero VARCHAR(50) NOT NULL
);

-- ═══ REFERENCE: Position/Creneau ═══
CREATE TABLE IF NOT EXISTS position_creneau (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL REFERENCES centers(id),
    code VARCHAR(20) NOT NULL,
    libelle VARCHAR(200) NOT NULL
);

-- ═══ REFERENCE: Transporteur ═══
CREATE TABLE IF NOT EXISTS transporteur (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL REFERENCES centers(id),
    nom VARCHAR(200) NOT NULL,
    telephone VARCHAR(30)
);

-- ═══ REFERENCE: Categorie transport ═══
CREATE TABLE IF NOT EXISTS categorie_transport (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL REFERENCES centers(id),
    libelle VARCHAR(200) NOT NULL
);

-- ═══ EXTEND patients table ═══
ALTER TABLE patients ADD COLUMN IF NOT EXISTS code_patient VARCHAR(20);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS civilite VARCHAR(10);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS groupe_sanguin VARCHAR(5);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS nombre_enfants INT DEFAULT 0;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS en_sommeil BOOLEAN DEFAULT FALSE;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS lieu_naissance VARCHAR(200);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS situation_familiale VARCHAR(30);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS profession1 VARCHAR(200);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS profession2 VARCHAR(200);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS adresse TEXT;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS tel_personnel VARCHAR(30);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS tel_mobile VARCHAR(30);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS tel_bureau VARCHAR(30);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS email VARCHAR(200);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS etat_patient VARCHAR(30) DEFAULT 'PERMANENT';
ALTER TABLE patients ADD COLUMN IF NOT EXISTS qualite_assure VARCHAR(30) DEFAULT 'ASSURE_LUI_MEME';
ALTER TABLE patients ADD COLUMN IF NOT EXISTS observation TEXT;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS sous_kt BOOLEAN DEFAULT FALSE;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS photo_base64 TEXT;

-- FK references
ALTER TABLE patients ADD COLUMN IF NOT EXISTS centre_payeur_id UUID;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS medecin_traitant_id UUID;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS salle_id UUID;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS position_id UUID;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS transporteur_aller_id UUID;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS transporteur_retour_id UUID;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS categorie_transport_id UUID;

-- Jours de dialyse
ALTER TABLE patients ADD COLUMN IF NOT EXISTS jour_dimanche BOOLEAN DEFAULT FALSE;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS jour_lundi BOOLEAN DEFAULT FALSE;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS jour_mardi BOOLEAN DEFAULT FALSE;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS jour_mercredi BOOLEAN DEFAULT FALSE;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS jour_jeudi BOOLEAN DEFAULT FALSE;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS jour_vendredi BOOLEAN DEFAULT FALSE;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS jour_samedi BOOLEAN DEFAULT FALSE;

-- Assure info
ALTER TABLE patients ADD COLUMN IF NOT EXISTS assure_nom VARCHAR(200);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS assure_prenom VARCHAR(200);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS assure_sexe VARCHAR(10);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS assure_date_naissance DATE;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS assure_tel_personnel VARCHAR(30);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS assure_adresse TEXT;
ALTER TABLE patients ADD COLUMN IF NOT EXISTS assure_groupe_sanguin VARCHAR(5);

