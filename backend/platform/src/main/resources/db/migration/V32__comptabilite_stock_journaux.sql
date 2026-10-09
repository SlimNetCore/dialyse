-- Comptabilité du stock (inventaire permanent) et paramétrage des journaux et des comptes par centre.
--
-- 1. journaux_comptables : journaux librement définis par chaque centre (code, libellé, actif). Un centre sans ligne
--    utilise les journaux par défaut (VE, BQ, CA, AC, ST) : aucune reprise de données n'est nécessaire.
-- 2. mapping_comptable : comptes du stock et journal de chaque opération. Colonnes facultatives : vide = valeur par
--    défaut (322, 602, 408, 757, 657 ; journal par défaut de l'opération), ce qui laisse intacts les centres déjà
--    paramétrés.
-- 3. articles : comptes propres à l'article (vides = comptes du centre).
-- 4. Index (center_id, source_id) : une pièce source n'est comptabilisée qu'une fois, la recherche se fait par source.
--
-- Idempotent. Les mêmes colonnes sont portées par les entités JPA pour que H2 (développement) reste aligné.

CREATE TABLE IF NOT EXISTS journaux_comptables
(
    id
    UUID
    NOT
    NULL
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    code
    VARCHAR
(
    10
) NOT NULL,
    libelle VARCHAR
(
    100
) NOT NULL,
    actif BOOLEAN NOT NULL,
    CONSTRAINT uq_journal_comptable_centre_code UNIQUE
(
    center_id,
    code
)
    );

ALTER TABLE mapping_comptable
    ADD COLUMN IF NOT EXISTS compte_stock VARCHAR (20);
ALTER TABLE mapping_comptable
    ADD COLUMN IF NOT EXISTS compte_consommation VARCHAR (20);
ALTER TABLE mapping_comptable
    ADD COLUMN IF NOT EXISTS compte_factures_non_parvenues VARCHAR (20);
ALTER TABLE mapping_comptable
    ADD COLUMN IF NOT EXISTS compte_boni_inventaire VARCHAR (20);
ALTER TABLE mapping_comptable
    ADD COLUMN IF NOT EXISTS compte_mali_inventaire VARCHAR (20);
ALTER TABLE mapping_comptable
    ADD COLUMN IF NOT EXISTS journal_vente VARCHAR (10);
ALTER TABLE mapping_comptable
    ADD COLUMN IF NOT EXISTS journal_reglement_banque VARCHAR (10);
ALTER TABLE mapping_comptable
    ADD COLUMN IF NOT EXISTS journal_reglement_caisse VARCHAR (10);
ALTER TABLE mapping_comptable
    ADD COLUMN IF NOT EXISTS journal_stock_reception VARCHAR (10);
ALTER TABLE mapping_comptable
    ADD COLUMN IF NOT EXISTS journal_stock_sortie VARCHAR (10);
ALTER TABLE mapping_comptable
    ADD COLUMN IF NOT EXISTS journal_stock_inventaire VARCHAR (10);

ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS compte_stock VARCHAR (20);
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS compte_charge VARCHAR (20);

CREATE INDEX IF NOT EXISTS idx_ecriture_center_source ON ecritures_comptables (center_id, source_id);
