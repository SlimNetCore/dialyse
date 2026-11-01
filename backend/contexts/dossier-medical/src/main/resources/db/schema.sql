-- Schema pour le contexte dossier-m\u00e9dical (slice DM3 examens).
-- Tables non g\u00e9r\u00e9es par des entit\u00e9s JPA (acc\u00e8s JDBC via DemandesExamenJdbc).

CREATE TABLE IF NOT EXISTS demandes_examen
(
    id
    UUID
    PRIMARY
    KEY,
    patient_id
    UUID
    NOT
    NULL,
    center_id
    UUID
    NOT
    NULL,
    prescripteur_id
    VARCHAR
(
    100
),
    date_demande DATE NOT NULL,
    categorie VARCHAR
(
    15
) NOT NULL,
    urgent BOOLEAN NOT NULL DEFAULT FALSE,
    motif VARCHAR
(
    500
),
    statut VARCHAR
(
    20
) NOT NULL,
    conclusion TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             CONSTRAINT chk_demande_examen_categorie CHECK (categorie IN ('BIOLOGIE', 'IMAGERIE', 'FONCTIONNEL', 'ANAPATH')),
    CONSTRAINT chk_demande_examen_statut CHECK
(
    statut
    IN
(
    'DEMANDE',
    'PRELEVE',
    'RESULTAT_DISPONIBLE',
    'VALIDE',
    'ANNULE'
))
    );

CREATE INDEX IF NOT EXISTS idx_demandes_examen_center_patient ON demandes_examen (center_id, patient_id);

CREATE TABLE IF NOT EXISTS lignes_demande_examen
(
    id
    UUID
    PRIMARY
    KEY,
    demande_id
    UUID
    NOT
    NULL,
    center_id
    UUID
    NOT
    NULL,
    analyte_code_system
    VARCHAR
(
    10
),
    analyte_code VARCHAR
(
    20
),
    analyte_code_display VARCHAR
(
    255
),
    libelle VARCHAR
(
    255
),
    commentaire VARCHAR
(
    500
),
    CONSTRAINT chk_ligne_demande_examen_contenu CHECK
(
    analyte_code
    IS
    NOT
    NULL
    OR
    libelle
    IS
    NOT
    NULL
)
    );

CREATE INDEX IF NOT EXISTS idx_lignes_demande_examen_demande ON lignes_demande_examen (demande_id);
CREATE INDEX IF NOT EXISTS idx_lignes_demande_examen_center ON lignes_demande_examen (center_id);

-- Tables pour le contexte dossier-médical (slice DM4 sérologie).
-- Tables non gérées par des entités JPA (accès JDBC via SerologiesJdbc).

CREATE TABLE IF NOT EXISTS serologies
(
    id
    UUID
    PRIMARY
    KEY,
    patient_id
    UUID
    NOT
    NULL,
    center_id
    UUID
    NOT
    NULL,
    prescripteur_id
    VARCHAR
(
    100
),
    date_prelevement DATE NOT NULL,
    type_serologie VARCHAR
(
    20
) NOT NULL,
    resultat VARCHAR
(
    20
) NOT NULL,
    valeur VARCHAR
(
    100
),
    unite VARCHAR
(
    50
),
    date_resultat TIMESTAMP WITH TIME ZONE,
                                laboratoire VARCHAR (200),
    commentaire TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
                            WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                CONSTRAINT chk_serologie_type CHECK (type_serologie IN ('VHB', 'VHC', 'VIH', 'SYPHILIS', 'HTLV', 'AUTRE')),
    CONSTRAINT chk_serologie_resultat CHECK
(
    resultat
    IN
(
    'POSITIF',
    'NEGATIF',
    'INDECIS',
    'EN_ATTENTE'
))
    );

CREATE INDEX IF NOT EXISTS idx_serologies_center_patient ON serologies (center_id, patient_id);
CREATE INDEX IF NOT EXISTS idx_serologies_center_type ON serologies (center_id, type_serologie);
CREATE INDEX IF NOT EXISTS idx_serologies_center_resultat ON serologies (center_id, resultat);

-- Tables pour le contexte dossier-médical (slice DM5 antécédents et allergies).
-- Tables non gérées par des entités JPA (accès JDBC via AntecedentsJdbc / AllergiesJdbc).

CREATE TABLE IF NOT EXISTS antecedents
(
    id
    UUID
    PRIMARY
    KEY,
    patient_id
    UUID
    NOT
    NULL,
    center_id
    UUID
    NOT
    NULL,
    type
    VARCHAR
(
    20
) NOT NULL,
    diagnostic_system VARCHAR
(
    30
),
    diagnostic_code VARCHAR
(
    20
),
    diagnostic_display VARCHAR
(
    255
),
    libelle_libre VARCHAR
(
    500
),
    date_debut DATE NOT NULL,
    date_fin DATE,
    statut_clinique VARCHAR
(
    15
) NOT NULL,
    severite VARCHAR
(
    30
),
    note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             CONSTRAINT chk_antecedent_type CHECK (type IN ('MEDICAL', 'CHIRURGICAL', 'FAMILIAL', 'OBSTETRICAL', 'COMORBIDITE')),
    CONSTRAINT chk_antecedent_statut CHECK
(
    statut_clinique
    IN
(
    'ACTIF',
    'RESOLU'
)),
    CONSTRAINT chk_antecedent_diagnostic CHECK
(
    diagnostic_code
    IS
    NOT
    NULL
    OR
    libelle_libre
    IS
    NOT
    NULL
)
    );

CREATE INDEX IF NOT EXISTS idx_antecedents_center_patient ON antecedents (center_id, patient_id);
CREATE INDEX IF NOT EXISTS idx_antecedents_center_type ON antecedents (center_id, type);

CREATE TABLE IF NOT EXISTS allergies
(
    id
    UUID
    PRIMARY
    KEY,
    patient_id
    UUID
    NOT
    NULL,
    center_id
    UUID
    NOT
    NULL,
    allergene
    VARCHAR
(
    200
) NOT NULL,
    type VARCHAR
(
    20
) NOT NULL,
    reaction VARCHAR
(
    200
),
    date_debut DATE NOT NULL,
    date_fin DATE,
    statut_clinique VARCHAR
(
    15
) NOT NULL,
    severite VARCHAR
(
    30
),
    note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             CONSTRAINT chk_allergie_type CHECK (type IN ('ALIMENTAIRE', 'MEDICAMENTOSE', 'ENVIRONNEMENTALE', 'AUTRE')),
    CONSTRAINT chk_allergie_statut CHECK
(
    statut_clinique
    IN
(
    'ACTIF',
    'RESOLU'
))
    );

CREATE INDEX IF NOT EXISTS idx_allergies_center_patient ON allergies (center_id, patient_id);
CREATE INDEX IF NOT EXISTS idx_allergies_center_type ON allergies (center_id, type);
