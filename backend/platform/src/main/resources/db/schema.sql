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
    center_id UUID,
    societe_id UUID,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_user_id ON auth_refresh_token (user_id);

-- Comptes « direction » : un utilisateur portant le rôle DIRECTION est rattaché à une société (sans centre).
CREATE TABLE IF NOT EXISTS app_user_societe
(
    user_id
    UUID
    NOT
    NULL,
    societe_id
    UUID
    NOT
    NULL,
    PRIMARY
    KEY
(
    user_id,
    societe_id
)
    );

-- Double authentification TOTP (facultative, par utilisateur). Le secret est chiffré (AES-GCM) ; les codes de
-- secours sont stockés hachés (BCrypt) et à usage unique.
CREATE TABLE IF NOT EXISTS app_user_mfa
(
    user_id
    UUID
    PRIMARY
    KEY,
    secret_cipher
    VARCHAR
(
    255
) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_step BIGINT NOT NULL DEFAULT 0,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                             );

CREATE TABLE IF NOT EXISTS app_user_mfa_recovery
(
    id
    UUID
    PRIMARY
    KEY,
    user_id
    UUID
    NOT
    NULL,
    code_hash
    VARCHAR
(
    100
) NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE
    );
CREATE INDEX IF NOT EXISTS idx_mfa_recovery_user ON app_user_mfa_recovery (user_id);

-- Instantanés mensuels des tableaux de bord de la direction (immuables une fois créés).
CREATE TABLE IF NOT EXISTS direction_snapshot
(
    id
    UUID
    PRIMARY
    KEY,
    societe_id
    UUID
    NOT
    NULL,
    mois
    VARCHAR
(
    7
) NOT NULL,
    payload TEXT NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               CONSTRAINT uq_direction_snapshot UNIQUE (societe_id, mois)
    );

-- Historique des alertes du tableau de bord de la direction (apparition / résolution).
CREATE TABLE IF NOT EXISTS direction_alert_history
(
    id
    UUID
    PRIMARY
    KEY,
    societe_id
    UUID
    NOT
    NULL,
    center_id
    UUID
    NOT
    NULL,
    centre_nom
    VARCHAR
(
    200
) NOT NULL,
    code VARCHAR
(
    60
) NOT NULL,
    severity VARCHAR
(
    20
) NOT NULL,
    valeur NUMERIC
(
    14,
    2
),
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE
                              );
CREATE INDEX IF NOT EXISTS idx_direction_alert_history_societe
    ON direction_alert_history (societe_id, resolved_at, first_seen_at DESC);

-- Journal d'audit (traçabilité « qui a fait quoi ») : écrit par lot, jamais sur le thread de la requête.
CREATE TABLE IF NOT EXISTS audit_log
(
    id
    UUID
    PRIMARY
    KEY,
    occurred_at
    TIMESTAMP
    WITH
    TIME
    ZONE
    NOT
    NULL,
    user_id
    UUID,
    username
    VARCHAR
(
    100
),
    roles VARCHAR
(
    200
),
    center_id UUID,
    societe_id UUID,
    action_code VARCHAR
(
    80
) NOT NULL,
    entity_type VARCHAR
(
    80
),
    entity_id VARCHAR
(
    100
),
    libelle VARCHAR
(
    500
),
    http_method VARCHAR
(
    10
),
    route_template VARCHAR
(
    300
),
    status_code INT,
    duration_ms BIGINT,
    ip_address VARCHAR
(
    64
)
    );
CREATE INDEX IF NOT EXISTS idx_audit_log_center ON audit_log (center_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_societe ON audit_log (societe_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_user ON audit_log (user_id, occurred_at DESC);

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

-- ═══ Facturation ═══

-- Types TVA versionnés dans le temps (partagés entre facturation et comptabilité)
CREATE TABLE IF NOT EXISTS tva_types
(
    id                  UUID PRIMARY KEY,
    center_id           UUID                     NOT NULL,
    libelle             VARCHAR(255)             NOT NULL,
    taux                DECIMAL(5, 2)            NOT NULL,
    type_prestation     VARCHAR(120)             NOT NULL DEFAULT 'HEMODIALYSE',
    exonere             BOOLEAN                  NOT NULL DEFAULT FALSE,
    date_debut_validite DATE                     NOT NULL,
    date_fin_validite   DATE,
    texte_reference     VARCHAR(500),
    actif               BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(120)
);

CREATE INDEX IF NOT EXISTS idx_tva_types_center_prestation ON tva_types (center_id, type_prestation, date_debut_validite);
CREATE INDEX IF NOT EXISTS idx_tva_types_center_actif ON tva_types (center_id, actif);

CREATE TABLE IF NOT EXISTS facturation_settings (
    center_id UUID PRIMARY KEY,
    tva_rate DECIMAL(5, 2) DEFAULT 0.00, -- Décommissionné : TVA gérée via tva_types (TypeTVA)
    code_format VARCHAR(120) NOT NULL,
    regroupement_multi_forfait BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by VARCHAR(120)
);

CREATE TABLE IF NOT EXISTS facture_sequence (
    center_id UUID NOT NULL,
    seq_year INTEGER NOT NULL,
    seq_value INTEGER NOT NULL,
    PRIMARY KEY (center_id, seq_year)
);

CREATE TABLE IF NOT EXISTS factures (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    numero_facture VARCHAR(120) NOT NULL,
    patient_code VARCHAR(80),
    patient_full_name VARCHAR(255),
    patient_status_snapshot VARCHAR(120),
    numero_immatriculation_snapshot VARCHAR(120),
    centre_payeur_id_snapshot UUID,
    agence_id_snapshot UUID,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    date_facturation DATE NOT NULL,
    tva_rate DECIMAL(5,2) NOT NULL,
    total_ht DECIMAL(14,2) NOT NULL,
    total_tva DECIMAL(14,2) NOT NULL,
    total_ttc DECIMAL(14,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (center_id, numero_facture)
);

CREATE INDEX IF NOT EXISTS idx_factures_center_period ON factures (center_id, date_facturation);
CREATE INDEX IF NOT EXISTS idx_factures_center_patient ON factures (center_id, patient_id);

CREATE TABLE IF NOT EXISTS facture_lignes (
    id UUID PRIMARY KEY,
    facture_id UUID NOT NULL,
    center_id UUID NOT NULL,
    forfait_id UUID,
    forfait_label VARCHAR(255) NOT NULL,
    unit_price_ht DECIMAL(14,2) NOT NULL,
    seance_count INTEGER NOT NULL,
    line_ht DECIMAL(14,2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_facture_lignes_facture ON facture_lignes (facture_id, center_id);

CREATE TABLE IF NOT EXISTS facture_reglements
(
    id             UUID PRIMARY KEY,
    facture_id     UUID                     NOT NULL,
    center_id      UUID                     NOT NULL,
    montant        DECIMAL(14, 2)           NOT NULL,
    date_reglement DATE                     NOT NULL,
    saisi_par      VARCHAR(120),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_facture_reglements_facture ON facture_reglements (facture_id, center_id);
CREATE INDEX IF NOT EXISTS idx_facture_reglements_period ON facture_reglements (center_id, date_reglement);

ALTER TABLE IF EXISTS seances ADD COLUMN IF NOT EXISTS facture_id UUID;
ALTER TABLE IF EXISTS facture_reglements
    ADD COLUMN IF NOT EXISTS code_reglement VARCHAR(30);
ALTER TABLE IF EXISTS seances
    ADD COLUMN IF NOT EXISTS forfait_override_id UUID;
ALTER TABLE IF EXISTS seances
    ADD COLUMN IF NOT EXISTS forfait_override_code VARCHAR(50);
ALTER TABLE IF EXISTS seances
    ADD COLUMN IF NOT EXISTS forfait_override_nom VARCHAR(255);
ALTER TABLE IF EXISTS seances
    ADD COLUMN IF NOT EXISTS forfait_override_prix DECIMAL(14, 2);
ALTER TABLE IF EXISTS seances
    ADD COLUMN IF NOT EXISTS forfait_override_updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE IF EXISTS seances
    ADD COLUMN IF NOT EXISTS forfait_override_updated_by VARCHAR(100);


