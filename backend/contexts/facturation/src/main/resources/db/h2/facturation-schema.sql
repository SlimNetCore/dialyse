-- DDL H2 (tests et poste de développement) des tables dont la Facturation est propriétaire (S-DDL3b, ADR 0187).
-- Chargé par spring.sql.init.schema-locations (application.yml de la plateforme) ; en production le schéma vient de Flyway.
-- Les anciens ALTER de ce bloc sur seances (facture_id, forfait_override_*, statut_avant_exclusion, exclusion_absence_id) étaient des
-- no-op : le CREATE de seances (contexts/seance/.../db/h2/seance-schema.sql) déclare déjà ces colonnes (H2 en mémoire, jamais
-- de base antérieure) ; ils sont supprimés, la parité est prouvée par SchemaH2DesScriptsParContexteTest.

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

ALTER TABLE IF EXISTS facture_reglements
    ADD COLUMN IF NOT EXISTS code_reglement VARCHAR(30);

-- Auteur de l'émission d'une facture (migration V40, slice F3, KB-FAC-30)
ALTER TABLE IF EXISTS factures
    ADD COLUMN IF NOT EXISTS emise_par UUID;

-- Mode, référence de paiement et clé d'idempotence d'un règlement (migration V41, slice F4, KB-FAC-39/40)
ALTER TABLE IF EXISTS facture_reglements
    ADD COLUMN IF NOT EXISTS mode_reglement VARCHAR(20);
ALTER TABLE IF EXISTS facture_reglements
    ADD COLUMN IF NOT EXISTS reference_paiement VARCHAR(120);
ALTER TABLE IF EXISTS facture_reglements
    ADD COLUMN IF NOT EXISTS cle_idempotence VARCHAR(120);
CREATE UNIQUE INDEX IF NOT EXISTS ux_facture_reglements_cle_idempotence
    ON facture_reglements (center_id, facture_id, cle_idempotence);

-- Clôture mensuelle de facturation (migration V42, slice F7, ADR 0037)
CREATE TABLE IF NOT EXISTS periodes_facturation
(
    center_id         UUID                     NOT NULL,
    annee             INTEGER                  NOT NULL,
    mois              INTEGER                  NOT NULL,
    statut            VARCHAR(10)              NOT NULL DEFAULT 'OUVERT',
    cloturee_par      UUID,
    cloturee_le       TIMESTAMP WITH TIME ZONE,
    rouverte_par      UUID,
    rouverte_le       TIMESTAMP WITH TIME ZONE,
    motif_reouverture VARCHAR(500),
    PRIMARY KEY (center_id, annee, mois),
    CONSTRAINT ck_periodes_facturation_mois CHECK (mois BETWEEN 1 AND 12),
    CONSTRAINT ck_periodes_facturation_statut CHECK (statut IN ('OUVERT', 'CLOTURE'))
);

CREATE TABLE IF NOT EXISTS periodes_facturation_journal
(
    id                            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    center_id                     UUID                     NOT NULL,
    annee                         INTEGER                  NOT NULL,
    mois                          INTEGER                  NOT NULL,
    type_acte                     VARCHAR(12)              NOT NULL,
    effectue_par                  UUID                     NOT NULL,
    effectue_le                   TIMESTAMP WITH TIME ZONE NOT NULL,
    motif                         VARCHAR(500),
    seances_eligibles_restantes   INTEGER,
    CONSTRAINT ck_periodes_facturation_journal_type CHECK (type_acte IN ('CLOTURE', 'REOUVERTURE'))
);

CREATE INDEX IF NOT EXISTS idx_periodes_facturation_journal_mois
    ON periodes_facturation_journal (center_id, annee, mois, id);
