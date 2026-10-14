-- Registre de publication d'evenements Spring Modulith (outbox intra-applicatif, ADR 0013).
-- Copie du schema officiel spring-modulith-events-jdbc 2.0.x (schemas/v2/schema-postgresql.sql), pour que la table soit
-- geree par Flyway (spring.modulith.events.jdbc.schema-initialization.enabled=false peut alors etre active en production).
CREATE TABLE IF NOT EXISTS event_publication
(
    id                     UUID                     NOT NULL,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);
