-- Migration V40 : auteur de l'émission d'une facture (slice F3, KB-FAC-30, ADR 0032).
--
-- ADDITIVE : une colonne nullable, aucun DROP, aucun DELETE, aucune donnée modifiée.
-- Le legacy ne gardait pas l'utilisateur qui validait la facturation. Le nouveau code enregistre l'utilisateur de la session
-- (identifiant) dans emise_par ; les factures déjà émises gardent NULL (auteur inconnu).

ALTER TABLE factures
    ADD COLUMN IF NOT EXISTS emise_par UUID;
