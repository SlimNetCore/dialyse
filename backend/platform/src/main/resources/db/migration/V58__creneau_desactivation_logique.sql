-- Migration V58 : désactivation logique des créneaux (PL11-2, décision D4 du 2026-11-13).
--
-- Un créneau référencé (patients, mouvements, séances, plannings, affectations d'infirmiers) n'est plus supprimé physiquement mais
-- désactivé : il disparaît des listes de sélection et l'historique le conserve.
--
-- ADDITIVE et IDEMPOTENTE : une colonne avec valeur par défaut ; tous les créneaux existants restent actifs. Portable
-- PostgreSQL 16 / H2.

ALTER TABLE position_creneau
    ADD COLUMN IF NOT EXISTS actif BOOLEAN NOT NULL DEFAULT TRUE;
