-- Migration V29 : journal durable des alertes (retrouvées à la connexion, lues par utilisateur).
--
-- Ces tables sont aussi créées au démarrage par db/schema.sql (idempotent) ; ce script documente le schéma attendu
-- et permet de le préparer à la main sur PostgreSQL.

CREATE TABLE IF NOT EXISTS notification_evenement
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    type
    VARCHAR
(
    60
) NOT NULL,
    payload TEXT NOT NULL,
    target_roles VARCHAR
(
    255
) NOT NULL,
    cree_le TIMESTAMP NOT NULL
    );
CREATE INDEX IF NOT EXISTS idx_notification_evenement_centre ON notification_evenement (center_id, cree_le);

CREATE TABLE IF NOT EXISTS notification_lecture
(
    notification_id
    UUID
    NOT
    NULL,
    user_id
    VARCHAR
(
    100
) NOT NULL,
    lue_le TIMESTAMP NOT NULL,
    PRIMARY KEY
(
    notification_id,
    user_id
)
    );
