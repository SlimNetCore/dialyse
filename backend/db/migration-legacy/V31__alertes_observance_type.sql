-- ═══════════════════════════════════════════════════════════════════
-- Alertes d'observance EPO/fer — distinction retard constaté (période close) vs rappel
-- d'échéance (période en cours, encore actionnable).
-- NOTE : Flyway n'est pas actif sur ce projet (voir AGENTS.md / db/schema.sql) ; ce script
-- documente le schéma pour la parité PostgreSQL. Le schéma réel en dev/test est généré par
-- Hibernate (ddl-auto: update) à partir des @Entity.
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE alertes_observance
    ADD COLUMN IF NOT EXISTS type_alerte VARCHAR(20) NOT NULL DEFAULT 'RETARD_CONSTATE';

ALTER TABLE alertes_observance
    ALTER COLUMN type_alerte DROP DEFAULT;
