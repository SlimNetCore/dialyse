-- Santé de la base : taille, taux de cache, connexions. Aucun paramètre : ce fichier est aussi rejoué tel quel par la CI
-- (.github/workflows/flyway.yml) sur un PostgreSQL réel.
SELECT pg_database_size(current_database())                                          AS taille,
       COALESCE((SELECT blks_hit::float8 * 100 / NULLIF(blks_hit + blks_read, 0)
                 FROM pg_stat_database
                 WHERE datname = current_database()), 100)                           AS cache_pct,
       (SELECT count(*) FROM pg_stat_activity WHERE datname = current_database())    AS connexions,
       (SELECT setting::int FROM pg_settings WHERE name = 'max_connections')         AS connexions_max,
       (SELECT stats_reset FROM pg_stat_database WHERE datname = current_database()) AS stats_reset
