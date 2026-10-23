-- Fuseau horaire d'un centre (jour métier du Stock : réception sans date, sortie du jour, fenêtre de tendance, péremption).
--
-- ADDITIVE : une colonne nullable, aucune donnée modifiée. NULL = UTC (comportement historique : tous les horodatages du serveur
-- sont en UTC). Valeur : identifiant IANA, par exemple 'Africa/Algiers'.
-- Portable PostgreSQL / H2.
ALTER TABLE centers
    ADD COLUMN IF NOT EXISTS fuseau_horaire VARCHAR (50);