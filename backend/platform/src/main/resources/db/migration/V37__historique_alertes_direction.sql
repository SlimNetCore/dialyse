-- ═══════════════════════════════════════════════════════════════════
-- Historique des alertes du tableau de bord de la direction (apparition / résolution).
-- NOTE : Flyway n'est pas actif sur ce projet ; ce script documente la migration à jouer à la main sur
-- PostgreSQL. En dev/test, db/schema.sql crée la même table.
-- ═══════════════════════════════════════════════════════════════════

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
