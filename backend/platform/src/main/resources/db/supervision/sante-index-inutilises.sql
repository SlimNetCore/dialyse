-- Index jamais utilisés depuis la dernière remise à zéro des statistiques (hors clés primaires et contraintes d'unicité,
-- qui servent à garantir l'intégrité même sans être lus). Dix plus gros.
SELECT s.relname                      AS nom_table,
       s.indexrelname                 AS nom_index,
       pg_relation_size(s.indexrelid) AS taille
FROM pg_stat_user_indexes s
         JOIN pg_index i ON i.indexrelid = s.indexrelid
WHERE s.schemaname = 'public'
  AND s.idx_scan = 0
  AND NOT i.indisunique
  AND NOT i.indisprimary
ORDER BY pg_relation_size(s.indexrelid) DESC LIMIT 10
