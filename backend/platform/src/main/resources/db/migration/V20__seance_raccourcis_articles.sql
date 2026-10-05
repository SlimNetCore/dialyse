-- Migration V20 : consommables proposés en un toucher à l'infirmier (poste infirmier), par centre.
--
-- Table sans entité JPA (créée aussi par db/schema.sql). La liste d'un centre est remplacée en bloc ; l'ordre est
-- celui d'affichage. Sans ligne, l'écran propose les articles les plus sortis du jour.

CREATE TABLE IF NOT EXISTS seance_raccourcis_articles
(
    center_id
    UUID
    NOT
    NULL,
    article_id
    UUID
    NOT
    NULL,
    ordre
    INTEGER
    NOT
    NULL,
    PRIMARY
    KEY
(
    center_id,
    article_id
)
    );
