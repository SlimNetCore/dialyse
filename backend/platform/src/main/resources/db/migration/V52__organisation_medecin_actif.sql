-- Migration V52 : suppression logique des médecins (décision de l'utilisateur du 2026-11-03).
--
-- Un médecin est référencé par patients.medecin_traitant_id et par les prescriptions : il n'est plus supprimé physiquement mais
-- désactivé (disparaît des listes de sélection).
--
-- ADDITIVE et IDEMPOTENTE : une colonne avec valeur par défaut ; tous les médecins existants restent actifs. Portable
-- PostgreSQL 16 / H2.

ALTER TABLE medecin
    ADD COLUMN IF NOT EXISTS actif BOOLEAN NOT NULL DEFAULT TRUE;
