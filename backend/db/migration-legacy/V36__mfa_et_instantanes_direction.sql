-- ═══════════════════════════════════════════════════════════════════
-- Double authentification TOTP et instantanés mensuels des tableaux de bord de la direction.
-- NOTE : Flyway n'est pas actif sur ce projet ; ce script documente la migration à jouer à la main sur
-- PostgreSQL. En dev/test, db/schema.sql crée les mêmes tables.
-- ═══════════════════════════════════════════════════════════════════

-- Secret TOTP chiffré (AES-GCM, clé dérivée de JWT_SECRET : ne pas changer JWT_SECRET sans réinscrire les comptes).
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

-- Codes de secours : hachés (BCrypt), à usage unique.
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

-- Instantanés mensuels (immuables) : un par société et par mois écoulé.
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
