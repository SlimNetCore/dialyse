-- ═══════════════════════════════════════════════════════════════════
-- Sessions sans centre (propriétaire, direction de société) et comptes direction.
-- NOTE : Flyway n'est pas actif sur ce projet ; ce script documente la migration à jouer à la main sur
-- PostgreSQL. En dev/test, schema.sql applique les mêmes changements.
--
-- Le rôle DIRECTION est rattaché à une société (table app_user_societe) et ne voit que des agrégats anonymes.
-- Le jeton de rafraîchissement d'une session sans centre porte la société (ou rien, pour le propriétaire).
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE auth_refresh_token
    ALTER COLUMN center_id DROP NOT NULL;
ALTER TABLE auth_refresh_token
    ADD COLUMN IF NOT EXISTS societe_id UUID;

CREATE TABLE IF NOT EXISTS app_user_societe
(
    user_id
    UUID
    NOT
    NULL,
    societe_id
    UUID
    NOT
    NULL,
    PRIMARY
    KEY
(
    user_id,
    societe_id
)
    );
