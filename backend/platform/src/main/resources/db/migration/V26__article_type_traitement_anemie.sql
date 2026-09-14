-- ═══════════════════════════════════════════════════════════════════
-- Traçabilité pharmaceutique EPO/Fer — marquage des articles de stock.
-- NOTE : Flyway n'est pas actif sur ce projet (voir AGENTS.md / db/schema.sql) ; ce script
-- documente le schéma pour la parité PostgreSQL. Le schéma réel en dev/test est généré par
-- Hibernate (ddl-auto: update) à partir des @Entity.
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS type_traitement_anemie VARCHAR(20);

ALTER TABLE articles
    ADD CONSTRAINT IF NOT EXISTS chk_article_type_traitement_anemie
        CHECK (type_traitement_anemie IS NULL OR type_traitement_anemie IN ('EPO', 'FER_INJECTABLE'));
