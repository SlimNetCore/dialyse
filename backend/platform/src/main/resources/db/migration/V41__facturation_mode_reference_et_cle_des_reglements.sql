-- Migration V41 : mode de règlement, référence de paiement et clé d'idempotence des règlements (slice F4, KB-FAC-39, KB-FAC-40,
-- ADR 0033).
--
-- ADDITIVE : trois colonnes nullables et un index unique, aucun DROP, aucun DELETE, aucune donnée modifiée.
--  * mode_reglement     : BANQUE, CHEQUE, CAISSE ou ESPECES ; NULL pour les règlements existants (comptabilisés en banque).
--  * reference_paiement : texte libre (numéro de chèque, de virement...), facultatif.
--  * cle_idempotence    : clé choisie par l'appelant ; un renvoi avec la même clé pour la même facture ne crée pas de doublon.
-- L'unicité (centre, facture, clé) laisse libres tous les règlements sans clé : les valeurs NULL ne s'excluent pas entre elles
-- (PostgreSQL et H2).

ALTER TABLE facture_reglements
    ADD COLUMN IF NOT EXISTS mode_reglement VARCHAR(20);
ALTER TABLE facture_reglements
    ADD COLUMN IF NOT EXISTS reference_paiement VARCHAR(120);
ALTER TABLE facture_reglements
    ADD COLUMN IF NOT EXISTS cle_idempotence VARCHAR(120);

CREATE UNIQUE INDEX IF NOT EXISTS ux_facture_reglements_cle_idempotence
    ON facture_reglements (center_id, facture_id, cle_idempotence);
