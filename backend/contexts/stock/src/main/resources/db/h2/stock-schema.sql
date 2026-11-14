-- DDL H2 (tests et poste de développement) des tables dont le Stock est propriétaire (S-DDL3, ADR 0186).
-- Chargé par spring.sql.init.schema-locations (application.yml de la plateforme) ; en production le schéma vient de Flyway
-- (V1, V4, V9, V22, V38, V45). Ordre interne conservé : les ALTER de stock_movements suivent sa création.

-- PL1 (b) : tables du stock, lues et écrites en JDBC par le contexte stock. Les cinq entités JPA (Article, Lot, StockMovement, Emplacement,
-- Fournisseur) sont retirées ; ce DDL reproduit EXACTEMENT ce que Hibernate (ddl-auto: update) créait (comparé par INFORMATION_SCHEMA avant /
-- après : colonnes, types, longueurs, nullabilité, défauts, clés, index). Doit rester AVANT les ALTER de stock_movements (inventaire_id, saisi_le).
CREATE TABLE IF NOT EXISTS emplacements (
    id UUID NOT NULL PRIMARY KEY,
    actif BOOLEAN NOT NULL,
    center_id UUID NOT NULL,
    code VARCHAR(255),
    libelle VARCHAR(255) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_emplacement_center ON emplacements (center_id);

CREATE TABLE IF NOT EXISTS fournisseurs (
    id UUID NOT NULL PRIMARY KEY,
    actif BOOLEAN NOT NULL,
    center_id UUID NOT NULL,
    code VARCHAR(255),
    contact VARCHAR(255),
    email VARCHAR(255),
    raison_sociale VARCHAR(255) NOT NULL,
    telephone VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_fournisseur_center ON fournisseurs (center_id);

CREATE TABLE IF NOT EXISTS articles (
    id UUID NOT NULL PRIMARY KEY,
    active BOOLEAN NOT NULL,
    center_id UUID NOT NULL,
    code VARCHAR(255) NOT NULL,
    code_barres VARCHAR(64),
    coefficient_achat NUMERIC(14, 4),
    compte_charge VARCHAR(20),
    compte_stock VARCHAR(20),
    condition_conservation VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE,
    dci VARCHAR(150),
    dechet_dasri BOOLEAN DEFAULT FALSE NOT NULL,
    dosage_par_unite NUMERIC(14, 4),
    forme_galenique VARCHAR(80),
    fournisseur_id UUID,
    gere_par_lot BOOLEAN NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    peremption_obligatoire BOOLEAN DEFAULT FALSE NOT NULL,
    pmp_courant NUMERIC(38, 2),
    prix_achat NUMERIC(14, 4),
    produit_dangereux BOOLEAN DEFAULT FALSE NOT NULL,
    reference_fabricant VARCHAR(80),
    seuil_alerte NUMERIC(38, 2) NOT NULL,
    stock_max NUMERIC(14, 4),
    stock_quantity NUMERIC(38, 2) NOT NULL,
    tva_type_id UUID,
    type_traitement_anemie VARCHAR(20),
    unite VARCHAR(255) NOT NULL,
    unite_achat VARCHAR(30),
    unite_dosage VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS lots (
    id UUID NOT NULL PRIMARY KEY,
    article_id UUID NOT NULL,
    bon_reception_id UUID,
    center_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    date_peremption DATE,
    emplacement_id UUID,
    numero_lot VARCHAR(255) NOT NULL,
    pmp NUMERIC(38, 2),
    quantite_initiale NUMERIC(38, 2) NOT NULL,
    quantite_restante NUMERIC(38, 2) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_lot_article_peremption ON lots (article_id, date_peremption);
CREATE INDEX IF NOT EXISTS idx_lot_article ON lots (article_id);
CREATE INDEX IF NOT EXISTS idx_lot_center ON lots (center_id);

CREATE TABLE IF NOT EXISTS stock_movements (
    id UUID NOT NULL PRIMARY KEY,
    article_id UUID NOT NULL,
    center_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(255),
    lot_id UUID,
    mouvement_type VARCHAR(255) NOT NULL,
    pmp_apres NUMERIC(38, 2),
    prix_unitaire NUMERIC(38, 2),
    quantite NUMERIC(38, 2) NOT NULL,
    seance_id UUID
);
CREATE INDEX IF NOT EXISTS idx_stock_mvt_article_date ON stock_movements (article_id, created_at);
CREATE INDEX IF NOT EXISTS idx_stock_mvt_lot ON stock_movements (lot_id);
CREATE INDEX IF NOT EXISTS idx_stock_mvt_seance ON stock_movements (seance_id);
CREATE INDEX IF NOT EXISTS idx_stock_mvt_center_date ON stock_movements (center_id, created_at);

-- PL1 (c) : bons de commande, de réception et de sortie et leurs lignes (même méthode que (b) ; entités et six dépôts JPA retirés).
CREATE TABLE IF NOT EXISTS bons_commande (
    id UUID NOT NULL PRIMARY KEY,
    center_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(255),
    fournisseur_id UUID,
    reference VARCHAR(255) NOT NULL,
    statut VARCHAR(255) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_bl_center ON bons_commande (center_id);
CREATE INDEX IF NOT EXISTS idx_bl_reference ON bons_commande (center_id, reference);

CREATE TABLE IF NOT EXISTS bons_commande_lignes (
    id UUID NOT NULL PRIMARY KEY,
    article_id UUID NOT NULL,
    bon_commande_id UUID NOT NULL,
    prix_unitaire NUMERIC(38, 2),
    quantite NUMERIC(38, 2) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_bl_ligne_bon ON bons_commande_lignes (bon_commande_id);

CREATE TABLE IF NOT EXISTS bons_reception (
    id UUID NOT NULL PRIMARY KEY,
    bon_commande_id UUID,
    center_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(255),
    date_reception DATE,
    fournisseur_id UUID,
    reference VARCHAR(255) NOT NULL,
    statut VARCHAR(255) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_br_center ON bons_reception (center_id);
CREATE INDEX IF NOT EXISTS idx_br_reference ON bons_reception (center_id, reference);

CREATE TABLE IF NOT EXISTS bons_reception_lignes (
    id UUID NOT NULL PRIMARY KEY,
    article_id UUID NOT NULL,
    bon_reception_id UUID NOT NULL,
    date_peremption DATE,
    emplacement_id UUID,
    lot_id UUID,
    numero_lot VARCHAR(255),
    prix_unitaire NUMERIC(38, 2),
    quantite NUMERIC(38, 2) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_br_ligne_bon ON bons_reception_lignes (bon_reception_id);

CREATE TABLE IF NOT EXISTS bons_sortie (
    id UUID NOT NULL PRIMARY KEY,
    center_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(255),
    date_sortie DATE,
    patient_id UUID,
    poste VARCHAR(255),
    reference VARCHAR(255) NOT NULL,
    seance_id UUID NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_bs_center ON bons_sortie (center_id);
CREATE INDEX IF NOT EXISTS idx_bs_seance ON bons_sortie (seance_id);
CREATE INDEX IF NOT EXISTS idx_bs_reference ON bons_sortie (center_id, reference);

CREATE TABLE IF NOT EXISTS bons_sortie_lignes (
    id UUID NOT NULL PRIMARY KEY,
    article_id UUID NOT NULL,
    bon_sortie_id UUID NOT NULL,
    lot_id UUID,
    pmp_applique NUMERIC(38, 2),
    quantite NUMERIC(38, 2) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_bs_ligne_bon ON bons_sortie_lignes (bon_sortie_id);
CREATE INDEX IF NOT EXISTS idx_bs_ligne_lot ON bons_sortie_lignes (lot_id);

-- Inventaire de stock (voir aussi migration/V4__inventaire_stock.sql)
CREATE TABLE IF NOT EXISTS inventaires
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
CREATE INDEX IF NOT EXISTS idx_inventaires_center_statut ON inventaires (center_id, statut, date_inventaire);

CREATE TABLE IF NOT EXISTS inventaire_lignes
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
CREATE INDEX IF NOT EXISTS idx_inventaire_lignes_inventaire ON inventaire_lignes (inventaire_id, position_ligne);

ALTER TABLE IF EXISTS stock_movements ADD COLUMN IF NOT EXISTS inventaire_id UUID;
CREATE INDEX IF NOT EXISTS idx_stock_mvt_center_inventaire ON stock_movements (center_id, inventaire_id, created_at);
-- Heure réelle de saisie (KB-STK-01, miroir de la migration V38)
ALTER TABLE IF EXISTS stock_movements ADD COLUMN IF NOT EXISTS saisi_le TIMESTAMP WITH TIME ZONE;

-- Groupes d'articles (migration V9 ; tables lues et écrites en JDBC par le contexte stock depuis la slice S2 : l'entité
-- JPA qui les créait en H2 est retirée)
CREATE TABLE IF NOT EXISTS groupes_articles
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    nom
    VARCHAR
(
    100
) NOT NULL,
    nom_cle VARCHAR
(
    100
) NOT NULL,
    description VARCHAR
(
    500
),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                             CONSTRAINT uk_groupes_articles_centre_nom UNIQUE (center_id, nom_cle)
    );

CREATE TABLE IF NOT EXISTS groupes_articles_items
(
    groupe_id
    UUID
    NOT
    NULL
    REFERENCES
    groupes_articles
(
    id
) ON DELETE CASCADE,
    article_id UUID NOT NULL,
    PRIMARY KEY
(
    groupe_id,
    article_id
)
    );

CREATE INDEX IF NOT EXISTS idx_groupes_articles_centre ON groupes_articles (center_id);

-- Sortie pour seances (migration V45, slice S6b, ADR 0045) ; PL16 (ADR 0184) : la table orpheline stock_parametres n'existe plus en H2
CREATE TABLE IF NOT EXISTS sorties_pour_seances
(
    id
    UUID
    NOT
    NULL,
    center_id
    UUID
    NOT
    NULL,
    reference
    VARCHAR
(
    255
) NOT NULL,
    jour DATE NOT NULL,
    salle VARCHAR
(
    255
),
    demi_journee VARCHAR
(
    20
),
    statut VARCHAR
(
    20
) NOT NULL,
    enregistree_le TIMESTAMP WITH TIME ZONE NOT NULL,
                                 enregistree_par VARCHAR (255),
    validee_le TIMESTAMP
                             WITH TIME ZONE,
                                 validee_par VARCHAR (255),
    PRIMARY KEY
(
    id
),
    CONSTRAINT ck_sorties_pour_seances_statut CHECK
(
    statut
    IN
(
    'ENREGISTREE',
    'RAPPROCHEE'
)),
    CONSTRAINT ck_sorties_pour_seances_demi_journee CHECK
(
    demi_journee
    IS
    NULL
    OR
    demi_journee
    IN
(
    'MATIN',
    'APRES_MIDI'
))
    );
CREATE INDEX IF NOT EXISTS idx_sorties_pour_seances_centre ON sorties_pour_seances (center_id, jour);
CREATE TABLE IF NOT EXISTS sorties_pour_seances_lignes
(
    id
    UUID
    NOT
    NULL,
    sortie_id
    UUID
    NOT
    NULL,
    article_id
    UUID
    NOT
    NULL,
    lot_id
    UUID,
    quantite_sortie
    NUMERIC
(
    38,
    2
) NOT NULL,
    valeur_unitaire NUMERIC
(
    38,
    2
) NOT NULL,
    quantite_consommee NUMERIC
(
    38,
    2
),
    ecart NUMERIC
(
    38,
    2
),
    motif VARCHAR
(
    500
),
    PRIMARY KEY
(
    id
)
    );
CREATE INDEX IF NOT EXISTS idx_sorties_pour_seances_lignes_sortie ON sorties_pour_seances_lignes (sortie_id);
CREATE TABLE IF NOT EXISTS consommations_de_seance
(
    seance_id
    UUID
    NOT
    NULL,
    center_id
    UUID
    NOT
    NULL,
    patient_id
    UUID,
    sortie_id
    UUID,
    mise_a_jour
    TIMESTAMP
    WITH
    TIME
    ZONE
    NOT
    NULL,
    PRIMARY
    KEY
(
    seance_id
)
    );
CREATE INDEX IF NOT EXISTS idx_consommations_de_seance_sortie ON consommations_de_seance (sortie_id);
CREATE TABLE IF NOT EXISTS consommations_de_seance_imputations
(
    id
    UUID
    NOT
    NULL,
    seance_id
    UUID
    NOT
    NULL,
    article_id
    UUID
    NOT
    NULL,
    lot_id
    UUID,
    quantite
    NUMERIC
(
    38,
    2
) NOT NULL,
    alerte VARCHAR
(
    40
),
    PRIMARY KEY
(
    id
)
    );
CREATE INDEX IF NOT EXISTS idx_consommations_imputations_seance ON consommations_de_seance_imputations (seance_id);
