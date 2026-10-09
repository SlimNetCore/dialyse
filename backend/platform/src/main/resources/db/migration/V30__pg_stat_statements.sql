-- Mesure des requêtes les plus coûteuses (pg_stat_statements), préalable à toute optimisation d'index/partition.
-- L'extension doit aussi être chargée par le serveur (shared_preload_libraries, voir docker-compose.prod.yml). Un
-- environnement où elle n'est pas disponible (hébergement géré sans droit suffisant) n'empêche pas la migration.
DO
$$
BEGIN
    CREATE
EXTENSION IF NOT EXISTS pg_stat_statements;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'pg_stat_statements non installée : %', SQLERRM;
END
$$;
