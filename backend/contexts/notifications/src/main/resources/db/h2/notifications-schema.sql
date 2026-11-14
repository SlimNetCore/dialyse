-- DDL H2 (tests et poste de développement) des tables dont les Notifications sont propriétaires (S-DDL3, ADR 0186).
-- Chargé par spring.sql.init.schema-locations (application.yml de la plateforme) ; en production le schéma vient de Flyway (V29, V49).

-- Journal durable des alertes d'un centre : une alerte envoyée en temps réel y est aussi enregistrée pour être retrouvée
-- à la connexion. target_roles : rôles ciblés entre virgules (« ,ADMIN,SECRETAIRE, »), vide = tout le monde.
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
-- Alerte personnelle : compte destinataire (V49, revue P8 M2).
ALTER TABLE IF EXISTS notification_evenement ADD COLUMN IF NOT EXISTS target_user_id VARCHAR (64);

-- Lecture d'une alerte par un utilisateur.
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
