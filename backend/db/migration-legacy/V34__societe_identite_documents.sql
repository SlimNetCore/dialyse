-- ═══════════════════════════════════════════════════════════════════
-- Identité des documents : pied de page et logo de la société.
-- NOTE : Flyway n'est pas actif sur ce projet ; ce script documente la migration à jouer à la main sur
-- PostgreSQL. En dev/test, Hibernate (ddl-auto: update) ajoute les colonnes à partir de SocieteJpaEntity.
--
-- Le logo est validé à l'envoi (PNG/JPEG reconnus sur leur contenu, 512 Ko et 2000 x 2000 px maximum) et imprimé
-- dans l'en-tête de tous les documents, avec les coordonnées de la société et du centre.
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE societes
    ADD COLUMN IF NOT EXISTS pied_page VARCHAR (500),
    ADD COLUMN IF NOT EXISTS logo BYTEA,
    ADD COLUMN IF NOT EXISTS logo_content_type VARCHAR (30);
