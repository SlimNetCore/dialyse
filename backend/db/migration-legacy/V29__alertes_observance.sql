-- ═══════════════════════════════════════════════════════════════════
-- Traçabilité pharmaceutique EPO/Fer — alertes d'observance de la prescription.
-- NOTE : Flyway n'est pas actif sur ce projet (voir AGENTS.md / db/schema.sql) ; ce script
-- documente le schéma pour la parité PostgreSQL. Le schéma réel en dev/test est généré par
-- Hibernate (ddl-auto: update) à partir des @Entity.
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS alertes_observance
(
    id                 UUID PRIMARY KEY,
    patient_id         UUID                     NOT NULL,
    center_id          UUID                     NOT NULL,
    type_traitement    VARCHAR(20)              NOT NULL,
    periode_debut      DATE                     NOT NULL,
    periode_fin        DATE                     NOT NULL,
    doses_attendues    INTEGER                  NOT NULL,
    doses_administrees INTEGER                  NOT NULL,
    message            VARCHAR(500),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at        TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_alertes_observance_patient ON alertes_observance (patient_id, center_id);
