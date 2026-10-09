-- Quinze plus grosses tables du schéma public (tables ordinaires et partitionnées) et leurs compteurs d'accès.
SELECT c.relname                                  AS nom,
       pg_total_relation_size(c.oid)              AS taille,
       GREATEST(c.reltuples, 0)::bigint           AS lignes, COALESCE(s.n_dead_tup, 0) AS mortes,
       COALESCE(s.n_live_tup, 0)                  AS vivantes,
       COALESCE(s.seq_scan, 0)                    AS scans_complets,
       COALESCE(s.seq_tup_read, 0)                AS lignes_lues_scans,
       COALESCE(s.idx_scan, 0)                    AS scans_index,
       GREATEST(s.last_autovacuum, s.last_vacuum) AS dernier_vacuum
FROM pg_class c
         JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
         LEFT JOIN pg_stat_user_tables s ON s.relid = c.oid
WHERE c.relkind IN ('r', 'p')
ORDER BY pg_total_relation_size(c.oid) DESC LIMIT 15
