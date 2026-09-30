-- ═══════════════════════════════════════════════════════════════════
-- Traçabilité pharmaceutique EPO/Fer — sortie de stock FEFO à l'administration.
-- NOTE : Flyway n'est pas actif sur ce projet (voir AGENTS.md / db/schema.sql) ; ce script
-- documente le schéma pour la parité PostgreSQL. Le schéma réel en dev/test est généré par
-- Hibernate (ddl-auto: update) à partir des @Entity.
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE administrations_anemie
    ADD COLUMN IF NOT EXISTS article_id UUID,
    ADD COLUMN IF NOT EXISTS quantite_article NUMERIC(10, 3);
