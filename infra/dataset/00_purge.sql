-- =====================================================================================================================
--  PURGE COMPLÈTE DE LA BASE HÉMODIALYSE (PostgreSQL)
-- ---------------------------------------------------------------------------------------------------------------------
--  Vide TOUTES les tables du schéma public (données uniquement : la structure est conservée).
--  La liste des tables est calculée dynamiquement depuis pg_tables : toute nouvelle table est couverte
--  automatiquement. Seul l'historique Flyway (s'il existe) est préservé.
--
--  ⚠  IRRÉVERSIBLE : faire une sauvegarde avant exécution (pg_dump).
--  ⚠  Après la purge, AUCUN compte n'existe plus : relancer 01_dataset_renadial.sql (qui recrée le SUPERADMIN)
--     ou passer par l'écran d'installation initiale /setup.
--  ⚠  Les licences sont également supprimées : elles devront être ré-émises par le SUPERADMIN.
--
--  Exécution :
--    psql -U hemo_user -d hemodialyse -v ON_ERROR_STOP=1 -f 00_purge.sql
--    (ou, via Docker)  docker exec -i hemodialyse-postgres-1 psql -U hemo_user -d hemodialyse -v ON_ERROR_STOP=1 < 00_purge.sql
-- =====================================================================================================================

BEGIN;

DO
$$
    DECLARE
v_tables text;
        v_count
int;
BEGIN
SELECT string_agg(format('%I.%I', schemaname, tablename), ', ' ORDER BY tablename),
       count(*)
INTO v_tables, v_count
FROM pg_tables
WHERE schemaname = 'public'
  AND tablename NOT IN ('flyway_schema_history');

IF
v_tables IS NULL THEN
            RAISE NOTICE 'Aucune table à purger.';
            RETURN;
END IF;

EXECUTE 'TRUNCATE TABLE ' || v_tables || ' RESTART IDENTITY CASCADE';
RAISE
NOTICE 'Purge terminée : % table(s) vidée(s).', v_count;
END
$$;

COMMIT;

-- Contrôle : toutes les tables doivent afficher 0 ligne.
SELECT relname AS table_name, n_live_tup AS lignes_estimees
FROM pg_stat_user_tables
WHERE schemaname = 'public'
ORDER BY relname;

