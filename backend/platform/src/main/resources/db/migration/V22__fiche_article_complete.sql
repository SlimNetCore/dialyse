-- Migration V22 : fiche article complète (identité, unités et conditionnement, achat, conservation et sécurité).
--
-- Le dosage par unité de stock (dosage_par_unite + unite_dosage) relie l'unité de prescription du médecin
-- (UI, mg…) à l'unité de stock/sortie : quantité sortie = dose prescrite / dosage_par_unite.
-- Hibernate (ddl-auto: update) ajoute ces colonnes au démarrage ; ce script documente le schéma et permet de le
-- préparer à la main (idempotent). Tous les champs sont facultatifs sauf les booléens (faux par défaut).

ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS dci VARCHAR (150);
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS forme_galenique VARCHAR (80);
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS code_barres VARCHAR (64);
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS reference_fabricant VARCHAR (80);
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS unite_achat VARCHAR (30);
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS coefficient_achat DECIMAL (14, 4);
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS dosage_par_unite DECIMAL (14, 4);
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS unite_dosage VARCHAR (20);
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS fournisseur_id UUID;
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS tva_type_id UUID;
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS prix_achat DECIMAL (14, 4);
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS stock_max DECIMAL (14, 4);
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS peremption_obligatoire BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS condition_conservation VARCHAR (20);
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS produit_dangereux BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS dechet_dasri BOOLEAN NOT NULL DEFAULT FALSE;
