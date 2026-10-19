-- Migration V38 : heure réelle de saisie des mouvements de stock (KB-STK-01, décision métier du 2026-10-19).
--
-- ADDITIVE : une colonne nullable, aucun DROP, aucun DELETE, aucune donnée modifiée.
-- created_at porte la date de la pièce (00:00 UTC) : une réception et une sortie du même jour ont la même date. Le rejeu du
-- PMP les ordonne désormais par date de pièce, puis par heure réelle de saisie (saisi_le), puis, à défaut, entrées avant
-- sorties. Les mouvements antérieurs gardent saisi_le NULL : pour eux la règle « entrées avant sorties » s'applique, comme
-- validé pour KB-STK-01 ; aucun rattrapage n'est fait (l'heure de saisie historique n'est pas connue de façon fiable).

ALTER TABLE stock_movements
    ADD COLUMN IF NOT EXISTS saisi_le TIMESTAMP WITH TIME ZONE;

