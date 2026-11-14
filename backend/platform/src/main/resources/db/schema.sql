-- Schéma H2 (tests et poste de développement) de ce qui reste à la PLATEFORME : identité, licence, audit, reprise de données et registre
-- d'événements (outbox) (ADR 0140 D7). Toutes les autres tables vivent dans le script H2 de leur contexte propriétaire
-- (contexts/<ctx>/src/main/resources/db/h2/<ctx>-schema.sql, S-DDL3 / S-DDL3b, ADR 0186 et 0187), listés dans l'ordre par
-- spring.sql.init.schema-locations (application.yml).
-- ORDRE : organisation-schema.sql (societes, centers) précède ce script : app_user_center référence centers par clé étrangère.
-- En production le schéma vient de Flyway.

-- ═══ User & Role Management ═══

-- S-DDL1 (2026-11-14) : license, app_user, app_role et les deux tables de rattachement décrivent EXACTEMENT Flyway V1 (baseline) +
-- V12 (must_change_password) : mêmes colonnes, mêmes largeurs, mêmes nullités, mêmes contraintes d'unicité (licence.jti, et AUCUNE
-- unicité sur app_user.username ni app_role.code, comme en production : KB-PLN-24), mêmes clés étrangères.
-- ORDRE : les clés étrangères imposent app_role, app_user AVANT app_user_role et app_user_center. H2 seulement.
CREATE TABLE IF NOT EXISTS license (
    id UUID NOT NULL,
    activated_at TIMESTAMP(6) WITH TIME ZONE,
    center_id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_by UUID,
    jti VARCHAR(255) NOT NULL,
    last_online_check_at TIMESTAMP(6) WITH TIME ZONE,
    license_key TEXT NOT NULL,
    max_users INTEGER NOT NULL,
    revoked_reason VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    valid_from TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_license_jti UNIQUE (jti)
);

CREATE TABLE IF NOT EXISTS app_role (
    id UUID NOT NULL,
    code VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS app_user (
    id UUID NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    email VARCHAR(255),
    full_name VARCHAR(255),
    must_change_password BOOLEAN DEFAULT FALSE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS app_user_role (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_app_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_app_user_role_role FOREIGN KEY (role_id) REFERENCES app_role (id)
);

CREATE TABLE IF NOT EXISTS app_user_center (
    user_id UUID NOT NULL,
    center_id UUID NOT NULL,
    PRIMARY KEY (user_id, center_id),
    CONSTRAINT fk_app_user_center_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_app_user_center_center FOREIGN KEY (center_id) REFERENCES centers (id)
);

-- IDN-licence (ADR 0175) : app_user_center est désormais une collection de valeurs de app_user (@ElementCollection), que Hibernate crée
-- AVANT ce script sans clé étrangère vers centers (il n'y a plus d'entité de centre) : la clé de la production (Flyway V1) est posée ici.
ALTER TABLE app_user_center ADD CONSTRAINT IF NOT EXISTS fk_app_user_center_center FOREIGN KEY (center_id) REFERENCES centers (id);

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
    user_id UUID NOT NULL,
    societe_id UUID NOT NULL,
    PRIMARY KEY (user_id, societe_id)
);

-- Double authentification TOTP (facultative, par utilisateur). Le secret est chiffré (AES-GCM) ; les codes de
-- secours sont stockés hachés (BCrypt) et à usage unique.
CREATE TABLE IF NOT EXISTS app_user_mfa
(
    user_id UUID PRIMARY KEY,
    secret_cipher VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_step BIGINT NOT NULL DEFAULT 0,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_user_mfa_recovery
(
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_mfa_recovery_user ON app_user_mfa_recovery (user_id);

-- Journal d'audit (traçabilité « qui a fait quoi ») : écrit par lot, jamais sur le thread de la requête.
CREATE TABLE IF NOT EXISTS audit_log
(
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id UUID,
    username VARCHAR(100),
    roles VARCHAR(200),
    center_id UUID,
    societe_id UUID,
    action_code VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80),
    entity_id VARCHAR(100),
    libelle VARCHAR(500),
    http_method VARCHAR(10),
    route_template VARCHAR(300),
    status_code INT,
    duration_ms BIGINT,
    ip_address VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_audit_log_center ON audit_log (center_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_societe ON audit_log (societe_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_user ON audit_log (user_id, occurred_at DESC);

-- ••• Reprise des données d'un système existant (voir aussi migration/V3__reprise_donnees.sql) •••

CREATE TABLE IF NOT EXISTS migration_batch
(
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    libelle VARCHAR(200) NOT NULL,
    source_system VARCHAR(100),
    date_debut_reprise DATE,
    status VARCHAR(20) NOT NULL,
    created_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_migration_batch_center ON migration_batch (center_id, status);

CREATE TABLE IF NOT EXISTS migration_entity_run
(
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL,
    center_id UUID NOT NULL,
    entity VARCHAR(40) NOT NULL,
    file_name VARCHAR(255),
    dry_run BOOLEAN NOT NULL,
    applied BOOLEAN NOT NULL,
    total_rows INTEGER NOT NULL,
    created_count INTEGER NOT NULL,
    updated_count INTEGER NOT NULL,
    error_count INTEGER NOT NULL,
    warning_count INTEGER NOT NULL,
    report_json TEXT,
    executed_by VARCHAR(100),
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_migration_run_batch ON migration_entity_run (batch_id, entity, executed_at);

CREATE TABLE IF NOT EXISTS migration_id_map
(
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    entity VARCHAR(40) NOT NULL,
    legacy_id VARCHAR(150) NOT NULL,
    target_id VARCHAR(150) NOT NULL,
    operation VARCHAR(10) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_migration_id_map UNIQUE (center_id, entity, legacy_id)
);
CREATE INDEX IF NOT EXISTS idx_migration_id_map_batch ON migration_id_map (batch_id);
-- S6b-3 (ADR 0134, KB-S6a-2) : état d'un assuré rapproché avant la reprise, restauré à l'annulation du lot (ajout, nullable).
ALTER TABLE IF EXISTS migration_id_map ADD COLUMN IF NOT EXISTS etat_origine TEXT;

CREATE TABLE IF NOT EXISTS migration_value_map
(
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    column_key VARCHAR(60) NOT NULL,
    source_value VARCHAR(150) NOT NULL,
    target_value VARCHAR(150) NOT NULL,
    CONSTRAINT uk_migration_value_map UNIQUE (center_id, column_key, source_value)
);

-- Registre de publication d'evenements Spring Modulith (outbox, ADR 0013, ADR 0036), cree ici pour H2 : le DDL officiel de
-- Modulith limite serialized_event a VARCHAR(4000), trop court pour un evenement d'integration. En production, c'est V35 (TEXT).
CREATE TABLE IF NOT EXISTS event_publication
(
    id                     UUID                     NOT NULL,
    listener_id            VARCHAR(512)             NOT NULL,
    event_type             VARCHAR(512)             NOT NULL,
    serialized_event       VARCHAR(1000000)         NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 VARCHAR(20),
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);
