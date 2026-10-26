-- Migration V47 (planification P3) : conserver le compte qui a saisi une absence d'infirmier (KB-PLN-39).
--
-- ADDITIVE : une colonne nullable, aucune donnée modifiée, aucun index. NULL = auteur inconnu (absence antérieure) : l'infirmier
-- concerné peut toujours la retirer tant qu'elle n'a pas commencé (comportement historique). Retour arrière : l'ancien code
-- ignore la colonne. Portable PostgreSQL / H2.
ALTER TABLE infirmier_absence
    ADD COLUMN IF NOT EXISTS declaree_par UUID;
