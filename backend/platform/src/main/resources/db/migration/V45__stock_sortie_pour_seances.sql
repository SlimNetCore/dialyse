-- Tables du processus « sortie pour seances » (slice S6b, ADR 0045) : le gestionnaire de stock sort la veille les consommables du
-- lendemain pour un perimetre (salle, demi-journee), les infirmiers imputent leurs consommations sans mouvement de stock, un
-- rapprochement solde les ecarts (sortie supplementaire ou retour en stock, mouvement RETOUR).
--
-- ADDITIVE : cinq tables nouvelles, aucun DROP, aucun DELETE, aucune donnee existante modifiee, aucune ligne inseree.
-- Le parametre par centre « sortie pour seances activee » vaut FALSE par defaut (absence de ligne = desactive) : tant qu'il est
-- desactive, la seance garde la sortie immediate FEFO actuelle.
-- Portable PostgreSQL / H2.
CREATE TABLE IF NOT EXISTS stock_parametres
(
    center_id
    UUID
    NOT
    NULL,
    sortie_pour_seances_activee
    BOOLEAN
    NOT
    NULL
    DEFAULT
    FALSE,
    PRIMARY
    KEY
(
    center_id
)
    );
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