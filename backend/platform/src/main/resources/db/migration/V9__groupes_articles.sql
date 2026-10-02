-- Migration V9 : groupes d'articles (ex. « KIT CNAS ») dont la valorisation du stock est suivie par la direction.
--
-- Hibernate (ddl-auto: update) crée ces tables au démarrage ; ce script documente le schéma attendu et permet de
-- le préparer à la main (idempotent). Un groupe appartient à un centre ; son nom est unique par centre
-- (nom_cle = nom en minuscules, sans accents ni espaces superflus).

CREATE TABLE IF NOT EXISTS groupes_articles
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    nom
    VARCHAR
(
    100
) NOT NULL,
    nom_cle VARCHAR
(
    100
) NOT NULL,
    description VARCHAR
(
    500
),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                             CONSTRAINT uk_groupes_articles_centre_nom UNIQUE (center_id, nom_cle)
    );

CREATE TABLE IF NOT EXISTS groupes_articles_items
(
    groupe_id
    UUID
    NOT
    NULL
    REFERENCES
    groupes_articles
(
    id
) ON DELETE CASCADE,
    article_id UUID NOT NULL,
    PRIMARY KEY
(
    groupe_id,
    article_id
)
    );

CREATE INDEX IF NOT EXISTS idx_groupes_articles_centre ON groupes_articles (center_id);
