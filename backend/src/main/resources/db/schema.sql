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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(center_id, code)
);

-- type_document : FICHE_PATIENT, ATTESTATION, PEC, FICHE_SIGNALETIQUE, CUSTOM
-- format_impression : PDF, EXCEL, HTML

