-- Paramétrage du planning d'un centre : jours d'ouverture et salles d'isolement (CSV).
-- Table créée par JPA (ddl-auto: update) en dev ; ce script documente le schéma pour PostgreSQL.
CREATE TABLE IF NOT EXISTS planning_parametres
(
    center_id
    uuid
    NOT
    NULL
    PRIMARY
    KEY,
    jours_ouverts
    varchar
(
    100
) NOT NULL,
    salles_isolement text,
    updated_at timestamp with time zone NOT NULL
                             );
