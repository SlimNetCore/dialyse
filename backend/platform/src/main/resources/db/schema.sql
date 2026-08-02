-- Schema for referential tables not managed by JPA entities
-- These are created manually because they don't have @Entity classes

CREATE TABLE IF NOT EXISTS caisse_assurance (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    nom VARCHAR(255) NOT NULL,
    type_caisse VARCHAR(50) NOT NULL DEFAULT 'STANDARD'
);

CREATE TABLE IF NOT EXISTS agence (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    caisse_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    nom VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS centre_payeur (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    agence_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    nom VARCHAR(255) NOT NULL,
    adresse VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS medecin (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    nom VARCHAR(255) NOT NULL,
    prenom VARCHAR(255),
    specialite VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS salle (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    nom VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS generateur
(
    id        UUID PRIMARY KEY,
    salle_id  UUID        NOT NULL,
    center_id UUID        NOT NULL,
    numero    VARCHAR(50) NOT NULL,
    marque    VARCHAR(100),
    modele VARCHAR(100),
    etat   VARCHAR(30) NOT NULL DEFAULT 'FONCTIONNEL'
);

CREATE TABLE IF NOT EXISTS position_creneau (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS transporteur (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    nom VARCHAR(255) NOT NULL,
    telephone VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS categorie_transport (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    libelle VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS forfait (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    prix DECIMAL(10,2)
);

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
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS assure_patient (
                                              id UUID NOT NULL DEFAULT RANDOM_UUID(),
    patient_id UUID NOT NULL,
    numero_assurance VARCHAR(100) NOT NULL,
    center_id UUID NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    date_debut_affectation DATE,
    date_fin_affectation   DATE,
                                              date_affectation TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              PRIMARY KEY (id)
);
-- Index de recherche pour les affectations actives (la contrainte d'unicité est gérée applicativement)
CREATE INDEX IF NOT EXISTS idx_assure_patient_primary_active
    ON assure_patient (patient_id, center_id, is_primary, date_fin_affectation);

-- ═══ User & Role Management ═══

CREATE TABLE IF NOT EXISTS app_role (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(150),
    full_name VARCHAR(150),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_user_role (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS app_user_center (
    user_id UUID NOT NULL,
    center_id UUID NOT NULL,
    PRIMARY KEY (user_id, center_id)
);

CREATE TABLE IF NOT EXISTS auth_refresh_token
(
    id         UUID PRIMARY KEY,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    user_id    UUID         NOT NULL,
    center_id  UUID         NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_user_id ON auth_refresh_token (user_id);

-- ═══ Calendrier centre (jours fériés / fermetures) ═══

CREATE TABLE IF NOT EXISTS center_holiday
(
    id        UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    day_date  DATE NOT NULL,
    label     VARCHAR(255),
    UNIQUE (center_id, day_date)
);

CREATE TABLE IF NOT EXISTS center_closure_day
(
    id        UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    day_date  DATE NOT NULL,
    reason    VARCHAR(255),
    UNIQUE (center_id, day_date)
);

-- ═══ Modèles de documents (Jasper) ═══

CREATE TABLE IF NOT EXISTS modele_document (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    type_document VARCHAR(50) NOT NULL,
    chemin_jrxml VARCHAR(500) NOT NULL,
    format_impression VARCHAR(20) NOT NULL DEFAULT 'PDF',
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(center_id, code)
);

-- type_document : FICHE_PATIENT, ATTESTATION, PEC, FICHE_SIGNALETIQUE, CUSTOM
-- format_impression : PDF, EXCEL, HTML

