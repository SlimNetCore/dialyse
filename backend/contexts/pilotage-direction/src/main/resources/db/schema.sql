-- Schéma du contexte pilotage-direction (slices PD1-PD4).
-- Tables non gérées par des entités JPA (accès JDBC).

CREATE TABLE IF NOT EXISTS tableaux_de_bord
(
    center_id
    UUID
    PRIMARY
    KEY,
    indicateurs
    TEXT
    NOT
    NULL,
    date_generation
    TIMESTAMP
    WITH
    TIME
    ZONE
    NOT
    NULL
    DEFAULT
    CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS instantanes_mensuels
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    mois
    INT
    NOT
    NULL,
    annee
    INT
    NOT
    NULL,
    donnees
    TEXT
    NOT
    NULL,
    fige
    BOOLEAN
    NOT
    NULL
    DEFAULT
    TRUE,
    created_at
    TIMESTAMP
    WITH
    TIME
    ZONE
    NOT
    NULL
    DEFAULT
    CURRENT_TIMESTAMP,
    CONSTRAINT
    chk_instantane_mois
    CHECK
(
    mois
    BETWEEN
    1
    AND
    12
),
    CONSTRAINT uq_instantane_centre_mois UNIQUE
(
    center_id,
    mois,
    annee
)
    );

CREATE INDEX IF NOT EXISTS idx_instantanes_center ON instantanes_mensuels (center_id);

CREATE TABLE IF NOT EXISTS alertes_direction
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    type
    VARCHAR
(
    30
) NOT NULL,
    seuil VARCHAR
(
    200
),
    message TEXT,
    date_alerte DATE NOT NULL,
    lue BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             CONSTRAINT chk_alerte_type CHECK (type IN ('SOUS_EFFECTIF', 'SUR_CAPACITE', 'RETARD_VALIDATION', 'GREFFE_EN_ATTENTE')),
    CONSTRAINT uq_alerte_centre_jour UNIQUE
(
    center_id,
    date_alerte
)
    );

CREATE INDEX IF NOT EXISTS idx_alertes_center_date ON alertes_direction (center_id, date_alerte);

CREATE TABLE IF NOT EXISTS exports_synthese
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    periode
    VARCHAR
(
    10
) NOT NULL,
    format VARCHAR
(
    10
) NOT NULL,
    statut VARCHAR
(
    20
) NOT NULL,
    contenu TEXT NOT NULL,
    date_generation TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  CONSTRAINT chk_export_format CHECK (format IN ('PDF', 'HTML')),
    CONSTRAINT chk_export_statut CHECK
(
    statut
    IN
(
    'DEMANDE',
    'GENERE'
))
    );

CREATE INDEX IF NOT EXISTS idx_exports_center ON exports_synthese (center_id);
