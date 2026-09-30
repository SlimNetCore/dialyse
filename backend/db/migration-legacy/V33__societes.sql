-- ═══════════════════════════════════════════════════════════════════
-- Sociétés : une société chapeaute un ou plusieurs centres.
-- NOTE : Flyway n'est pas actif sur ce projet (voir AGENTS.md / db/schema.sql) ; ce script documente la
-- migration à jouer à la main sur PostgreSQL. En dev/test, Hibernate (ddl-auto: update) crée la table
-- `societes` et ajoute les colonnes de `centers` (toutes nullables) à partir des @Entity.
--
-- Règles portées par le domaine (Societe) :
--   * un centre appartient à exactement une société ;
--   * une société possède au moins un centre, et au moins un centre actif tant qu'elle est active.
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS societes
(
    id
    UUID
    PRIMARY
    KEY,
    code
    VARCHAR
(
    30
) NOT NULL UNIQUE,
    raison_sociale VARCHAR
(
    200
) NOT NULL,
    nif VARCHAR
(
    40
),
    nis VARCHAR
(
    40
),
    rc VARCHAR
(
    40
),
    adresse VARCHAR
(
    250
),
    ville VARCHAR
(
    100
),
    wilaya VARCHAR
(
    100
),
    telephone VARCHAR
(
    30
),
    email VARCHAR
(
    150
),
    site_web VARCHAR
(
    200
),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                             );

ALTER TABLE centers
    ADD COLUMN IF NOT EXISTS societe_id UUID,
    ADD COLUMN IF NOT EXISTS adresse VARCHAR (250),
    ADD COLUMN IF NOT EXISTS ville VARCHAR (100),
    ADD COLUMN IF NOT EXISTS wilaya VARCHAR (100),
    ADD COLUMN IF NOT EXISTS telephone VARCHAR (30),
    ADD COLUMN IF NOT EXISTS email VARCHAR (150),
    ADD COLUMN IF NOT EXISTS site_web VARCHAR (200),
    ADD COLUMN IF NOT EXISTS actif BOOLEAN NOT NULL DEFAULT TRUE;

-- Reprise des centres existants : une société par défaut les chapeaute.
INSERT INTO societes (id, code, raison_sociale)
SELECT '51000001-0000-0000-0000-00000000ffff',
       'DEFAUT',
       'Société par défaut' WHERE EXISTS (SELECT 1 FROM centers WHERE societe_id IS NULL)
  AND NOT EXISTS (SELECT 1 FROM societes WHERE id = '51000001-0000-0000-0000-00000000ffff');

UPDATE centers
SET societe_id = '51000001-0000-0000-0000-00000000ffff'
WHERE societe_id IS NULL;

-- Le rattachement devient obligatoire une fois la reprise faite.
ALTER TABLE centers
    ALTER COLUMN societe_id SET NOT NULL;
ALTER TABLE centers
    ADD CONSTRAINT fk_centers_societe FOREIGN KEY (societe_id) REFERENCES societes (id);

CREATE INDEX IF NOT EXISTS idx_centers_societe ON centers (societe_id);
