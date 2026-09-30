-- ============================================================
-- V4 : inventaire de stock
-- Un inventaire fige le stock théorique d'un centre à une date ; tant qu'il est EN_COURS, aucun mouvement de
-- stock n'est permis. À la clôture, les quantités comptées deviennent le stock de départ (mouvements INVENTAIRE)
-- et tous les mouvements antérieurs sont rattachés à l'inventaire (stock_movements.inventaire_id) : les
-- recalculs de stock et de PMP les ignorent. Même DDL que db/schema.sql (H2).
-- ============================================================

CREATE TABLE IF NOT EXISTS public.inventaires
(
    id
    uuid
    NOT
    NULL
    PRIMARY
    KEY,
    center_id
    uuid
    NOT
    NULL,
    reference
    character
    varying
(
    40
) NOT NULL,
    date_inventaire date NOT NULL,
    statut character varying
(
    20
) NOT NULL,
    commentaire character varying
(
    1000
),
    created_by character varying
(
    100
),
    created_at timestamp with time zone NOT NULL,
                             closed_by character varying (100),
    closed_at timestamp
                         with time zone,
                             CONSTRAINT uk_inventaires_reference UNIQUE (center_id, reference)
    );
CREATE INDEX IF NOT EXISTS idx_inventaires_center_statut ON public.inventaires (center_id, statut, date_inventaire);

CREATE TABLE IF NOT EXISTS public.inventaire_lignes
(
    id
    uuid
    NOT
    NULL
    PRIMARY
    KEY,
    inventaire_id
    uuid
    NOT
    NULL,
    center_id
    uuid
    NOT
    NULL,
    position_ligne
    integer
    NOT
    NULL,
    article_id
    uuid
    NOT
    NULL,
    article_code
    character
    varying
(
    100
),
    article_libelle character varying
(
    255
),
    unite character varying
(
    50
),
    lot_id uuid,
    numero_lot character varying
(
    100
),
    date_peremption date,
    quantite_theorique numeric
(
    14,
    3
) NOT NULL,
    quantite_comptee numeric
(
    14,
    3
),
    pmp numeric
(
    14,
    4
),
    motif_ecart character varying
(
    255
),
    compte_par character varying
(
    100
),
    compte_le timestamp with time zone,
                            ajoutee boolean NOT NULL DEFAULT FALSE
                            );
CREATE INDEX IF NOT EXISTS idx_inventaire_lignes_inventaire ON public.inventaire_lignes (inventaire_id, position_ligne);

ALTER TABLE public.stock_movements
    ADD COLUMN IF NOT EXISTS inventaire_id uuid;
CREATE INDEX IF NOT EXISTS idx_stock_mvt_center_inventaire ON public.stock_movements (center_id, inventaire_id, created_at);

