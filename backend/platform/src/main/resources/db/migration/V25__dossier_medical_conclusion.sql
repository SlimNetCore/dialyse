-- ═══════════════════════════════════════════════════════════════════
-- Dossier médical — ajout de la conclusion du médecin au dossier de base du patient.
-- NOTE : Flyway n'est pas actif sur ce projet (voir AGENTS.md / db/schema.sql) ; ce script
-- documente le schéma pour la parité PostgreSQL. Le schéma réel en dev/test est généré par
-- Hibernate (ddl-auto: update) à partir des @Entity.
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE dossier_medical_patient
    ADD COLUMN IF NOT EXISTS conclusion_medicale TEXT;
