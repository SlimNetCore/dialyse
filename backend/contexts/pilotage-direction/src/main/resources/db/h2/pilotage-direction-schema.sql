-- DDL H2 (tests et poste de développement) des tables dont le Pilotage de la direction est propriétaire (S-DDL3b, ADR 0187).
-- Chargé par spring.sql.init.schema-locations (application.yml de la plateforme) ; en production le schéma vient de Flyway.

-- Instantanés mensuels des tableaux de bord de la direction (immuables une fois créés).
CREATE TABLE IF NOT EXISTS direction_snapshot
(
    id UUID PRIMARY KEY,
    societe_id UUID NOT NULL,
    mois VARCHAR(7) NOT NULL,
    payload TEXT NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_direction_snapshot UNIQUE (societe_id, mois)
);

-- Historique des alertes du tableau de bord de la direction (apparition / résolution).
CREATE TABLE IF NOT EXISTS direction_alert_history
(
    id UUID PRIMARY KEY,
    societe_id UUID NOT NULL,
    center_id UUID NOT NULL,
    centre_nom VARCHAR(200) NOT NULL,
    code VARCHAR(60) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    valeur NUMERIC(14, 2),
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_direction_alert_history_societe
    ON direction_alert_history (societe_id, resolved_at, first_seen_at DESC);
