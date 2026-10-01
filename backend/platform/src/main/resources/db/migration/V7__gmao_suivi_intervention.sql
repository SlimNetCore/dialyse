-- Migration V7 : suivi enrichi des interventions GMAO + pièces jointes.
--
-- Hibernate (ddl-auto: update) ajoute ces colonnes et crée la table des documents au démarrage ; ce script
-- documente le schéma attendu et permet de le préparer à la main (idempotent). Toutes les colonnes sont
-- nullables : les interventions existantes restent valides (priorité NORMALE par défaut côté application).

ALTER TABLE gmao_interventions
    ADD COLUMN IF NOT EXISTS priorite VARCHAR (20);
ALTER TABLE gmao_interventions
    ADD COLUMN IF NOT EXISTS echeance TIMESTAMP WITH TIME ZONE;
ALTER TABLE gmao_interventions
    ADD COLUMN IF NOT EXISTS symptome TEXT;
ALTER TABLE gmao_interventions
    ADD COLUMN IF NOT EXISTS cause TEXT;
ALTER TABLE gmao_interventions
    ADD COLUMN IF NOT EXISTS demarre_par UUID;
ALTER TABLE gmao_interventions
    ADD COLUMN IF NOT EXISTS demarre_le TIMESTAMP WITH TIME ZONE;
ALTER TABLE gmao_interventions
    ADD COLUMN IF NOT EXISTS cloture_par UUID;
ALTER TABLE gmao_interventions
    ADD COLUMN IF NOT EXISTS cloture_le TIMESTAMP WITH TIME ZONE;
ALTER TABLE gmao_interventions
    ADD COLUMN IF NOT EXISTS annule_par UUID;
ALTER TABLE gmao_interventions
    ADD COLUMN IF NOT EXISTS annule_le TIMESTAMP WITH TIME ZONE;

CREATE TABLE IF NOT EXISTS gmao_documents_intervention
(
    id
    UUID
    PRIMARY
    KEY,
    intervention_id
    UUID
    NOT
    NULL,
    centre_id
    UUID
    NOT
    NULL,
    type
    VARCHAR
(
    30
) NOT NULL,
    nom VARCHAR
(
    150
) NOT NULL,
    content_type VARCHAR
(
    50
) NOT NULL,
    taille BIGINT NOT NULL,
    contenu BYTEA NOT NULL,
    ajoute_par UUID,
    ajoute_le TIMESTAMP WITH TIME ZONE NOT NULL
                            );

CREATE INDEX IF NOT EXISTS idx_gmao_documents_intervention ON gmao_documents_intervention (intervention_id);
