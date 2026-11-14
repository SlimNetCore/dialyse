-- DDL H2 (tests et poste de développement) des tables dont la Comptabilité est propriétaire (S-DDL2b, ADR 0186).
-- Chargé par spring.sql.init.schema-locations (application.yml de la plateforme) ; en production le schéma vient de Flyway (V1).

-- PL1 (d) : compteurs de numérotation des pièces (JDBC : NumerotationDesDocumentsJdbc, ADR 0185). Entités AppSetting, AppSettingId et
-- UserCenterAssignment retirées ; PL16 (ADR 0184) : la table morte user_center_assignment n'existe plus en H2 (Flyway inchangé).
CREATE TABLE IF NOT EXISTS app_settings (
    center_id UUID NOT NULL,
    cle VARCHAR(50) NOT NULL,
    dernier_compteur BIGINT NOT NULL,
    prefixe VARCHAR(20) NOT NULL,
    PRIMARY KEY (center_id, cle)
);

-- S-DDL3b (ADR 0187) : types de TVA versionnés dans le temps. Propriétaire : la Comptabilité (règles de TVA, package tva ; la
-- Facturation dépend de la Comptabilité par Maven et lit la table, jamais l'inverse).
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

-- Paramétrage comptable sans développement (voir migration V33) : plan comptable du centre, compte client de chaque
-- payeur, modèles de pièces et leurs lignes.
CREATE TABLE IF NOT EXISTS comptes_comptables
(
    id UUID NOT NULL PRIMARY KEY,
    center_id UUID NOT NULL,
    numero VARCHAR(20) NOT NULL,
    libelle VARCHAR(150) NOT NULL,
    actif BOOLEAN NOT NULL,
    CONSTRAINT uq_compte_comptable_centre_numero UNIQUE (center_id, numero)
);

CREATE TABLE IF NOT EXISTS comptes_payeurs
(
    center_id UUID NOT NULL,
    payeur_id UUID NOT NULL,
    compte VARCHAR(20) NOT NULL,
    CONSTRAINT pk_comptes_payeurs PRIMARY KEY (center_id, payeur_id)
);

CREATE TABLE IF NOT EXISTS modeles_piece
(
    id UUID NOT NULL PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(20) NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    journal_code VARCHAR(10) NOT NULL,
    actif BOOLEAN NOT NULL,
    CONSTRAINT uq_modele_piece_centre_code UNIQUE (center_id, code)
);

CREATE TABLE IF NOT EXISTS modeles_piece_lignes
(
    modele_id UUID NOT NULL,
    center_id UUID NOT NULL,
    position_ligne INTEGER NOT NULL,
    sens VARCHAR(6) NOT NULL,
    compte VARCHAR(20) NOT NULL,
    libelle VARCHAR(100),
    CONSTRAINT pk_modeles_piece_lignes PRIMARY KEY (modele_id, position_ligne)
);
CREATE INDEX IF NOT EXISTS idx_modeles_piece_lignes_compte ON modeles_piece_lignes (center_id, compte);
