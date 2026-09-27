-- ═══════════════════════════════════════════════════════════════════
-- Journal d'audit (traçabilité « qui a fait quoi ») : écritures + consultations sensibles.
-- NOTE : Flyway n'est pas actif sur ce projet ; ce script documente la migration à jouer à la main sur
-- PostgreSQL. En dev/test, db/schema.sql crée la même table.
-- ═══════════════════════════════════════════════════════════════════

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
