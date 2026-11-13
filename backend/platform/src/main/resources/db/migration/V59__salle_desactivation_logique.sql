-- Migration V59 : désactivation logique des salles (PL11-4, décision D4 du 2026-11-13).
--
-- Une salle référencée (patients, mouvements, séances, plannings, affectations d'infirmiers, équipements) n'est plus supprimée
-- physiquement mais désactivée : elle disparaît des listes de sélection et l'historique la conserve.
--
-- ADDITIVE et IDEMPOTENTE : une colonne avec valeur par défaut ; toutes les salles existantes restent actives. Portable
-- PostgreSQL 16 / H2.

ALTER TABLE salle
    ADD COLUMN IF NOT EXISTS actif BOOLEAN NOT NULL DEFAULT TRUE;
