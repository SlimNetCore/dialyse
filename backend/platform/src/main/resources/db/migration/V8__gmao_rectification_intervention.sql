-- Migration V8 : rectification tracée des interventions GMAO + lignes de coût automatiques.
--
-- La table des lignes de coût doit exister avant le démarrage du backend ; cette migration la crée de façon
-- idempotente. Les lignes existantes restent « manuelles » (automatique NULL ou FALSE) : seules les lignes
-- générées à la clôture après cette version sont recalculées lors d'une rectification.
--
-- Droit particulier : créer le rôle de code GMAO_RECTIFICATION (écran Administration > Rôles) et l'attribuer
-- aux personnes habilitées à rectifier une intervention terminée.

CREATE TABLE IF NOT EXISTS gmao_lignes_cout_intervention
(
    id UUID PRIMARY KEY NOT NULL,
    intervention_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    quantite NUMERIC(12, 3) NOT NULL,
    prix_unitaire NUMERIC(12, 2) NOT NULL,
    article_stock_id UUID,
    automatique BOOLEAN
);

ALTER TABLE gmao_lignes_cout_intervention
    ADD COLUMN IF NOT EXISTS automatique BOOLEAN;

CREATE TABLE IF NOT EXISTS gmao_rectifications_intervention
(
    id
    UUID
    PRIMARY
    KEY,
    intervention_id
    UUID
    NOT
    NULL,
    motif
    TEXT
    NOT
    NULL,
    par
    UUID,
    le
    TIMESTAMP
    WITH
    TIME
    ZONE
    NOT
    NULL,
    cloture_anterieure_le
    TIMESTAMP
    WITH
    TIME
    ZONE,
    cloture_anterieure_par
    UUID
);

CREATE INDEX IF NOT EXISTS idx_gmao_rectifications_intervention ON gmao_rectifications_intervention (intervention_id);
