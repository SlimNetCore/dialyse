-- Migration V6 : horodatages GMAO en TIMESTAMP WITH TIME ZONE (instants UTC).
--
-- Le module GMAO stockait des dates sans fuseau (TIMESTAMP). Il utilise désormais des instants UTC
-- (OffsetDateTime, colonnes TIMESTAMP WITH TIME ZONE). Hibernate (ddl-auto: update) ne modifie pas le type
-- d'une colonne existante : à exécuter UNE FOIS sur toute base PostgreSQL où les tables GMAO existent déjà.
-- Idempotent : une colonne déjà en timestamptz est ignorée. Sans effet sur une base neuve (tables créées
-- directement avec le bon type) et sans objet en dev (H2 en mémoire).
--
-- Fuseau d'origine des valeurs existantes : 'UTC' par défaut. Si le serveur d'application tournait dans un
-- autre fuseau (ex. 'Africa/Algiers') lorsque les données ont été saisies, remplacer 'UTC' ci-dessous par ce
-- fuseau AVANT d'exécuter le script (les anciennes valeurs étaient alors des heures locales du serveur).

DO
$$
    DECLARE
source_tz CONSTANT text := 'UTC';
        col
RECORD;
BEGIN
FOR col IN
SELECT t.table_name, t.column_name
FROM (VALUES ('gmao_equipements', 'date_installation'),
             ('gmao_equipements', 'date_creation'),
             ('gmao_equipements', 'date_modification'),
             ('gmao_equipements', 'deleted_at'),
             ('gmao_equipement_statut_historique', 'changed_at'),
             ('gmao_interventions', 'date_debut'),
             ('gmao_interventions', 'date_fin'),
             ('gmao_interventions', 'date_creation'),
             ('gmao_interventions', 'date_modification'),
             ('gmao_interventions', 'deleted_at'),
             ('gmao_plans_maintenance', 'prochaine_date_prevue'),
             ('gmao_plans_maintenance', 'derniere_date_execution'),
             ('gmao_plans_maintenance', 'date_creation'),
             ('gmao_plans_maintenance', 'date_modification'),
             ('gmao_plans_maintenance', 'deleted_at')) AS t(table_name, column_name)
         JOIN information_schema.columns c
              ON c.table_schema = current_schema()
                  AND c.table_name = t.table_name
                  AND c.column_name = t.column_name
WHERE c.data_type = 'timestamp without time zone' LOOP
                EXECUTE format('ALTER TABLE %I ALTER COLUMN %I TYPE timestamp with time zone USING %I AT TIME ZONE %L',
                               col.table_name, col.column_name, col.column_name, source_tz);
END LOOP;
END
$$;
