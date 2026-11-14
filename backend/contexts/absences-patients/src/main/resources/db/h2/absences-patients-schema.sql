-- DDL H2 (tests et poste de développement) des tables dont les Absences de patients sont propriétaires (S-DDL3, ADR 0186).
-- Chargé par spring.sql.init.schema-locations (application.yml de la plateforme) ; en production le schéma vient de Flyway (V13, V43, V44).

-- Absences des patients : une par patient et par jour, valorisée au forfait de la prise en charge (TTC -> HT)
CREATE TABLE IF NOT EXISTS absence_patient
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    patient_id
    UUID
    NOT
    NULL,
    date_seance
    DATE
    NOT
    NULL,
    source
    VARCHAR
(
    20
) NOT NULL,
    statut VARCHAR
(
    20
) NOT NULL,
    motif VARCHAR
(
    30
),
    commentaire VARCHAR
(
    1000
),
    forfait_id UUID,
    forfait_libelle VARCHAR
(
    255
),
    prix_ttc NUMERIC
(
    14,
    2
) NOT NULL DEFAULT 0,
    taux_tva NUMERIC
(
    6,
    2
) NOT NULL DEFAULT 0,
    montant_ht NUMERIC
(
    14,
    2
) NOT NULL DEFAULT 0,
    declaree_par UUID,
    declaree_le TIMESTAMP WITH TIME ZONE,
    qualifiee_par UUID,
    qualifiee_le TIMESTAMP WITH TIME ZONE,
    date_rattrapage DATE,
    modifiee_par UUID,
    modifiee_le TIMESTAMP WITH TIME ZONE,
                              UNIQUE (center_id, patient_id, date_seance)
    );
CREATE INDEX IF NOT EXISTS idx_absence_patient_periode ON absence_patient (center_id, date_seance);
CREATE INDEX IF NOT EXISTS idx_absence_patient_statut ON absence_patient (center_id, statut);

-- Clôture mensuelle des absences de patients (migration V44, slice A6, ADR 0042)
CREATE TABLE IF NOT EXISTS mois_absences
(
    center_id
    UUID
    NOT
    NULL,
    annee
    INTEGER
    NOT
    NULL,
    mois
    INTEGER
    NOT
    NULL,
    statut
    VARCHAR
(
    10
) NOT NULL DEFAULT 'OUVERT',
    cloture_par UUID,
    cloture_le TIMESTAMP WITH TIME ZONE,
    rouvert_par UUID,
    rouvert_le TIMESTAMP WITH TIME ZONE,
                             motif_reouverture VARCHAR (500),
    PRIMARY KEY
(
    center_id,
    annee,
    mois
),
    CONSTRAINT ck_mois_absences_mois CHECK
(
    mois
    BETWEEN
    1
    AND
    12
),
    CONSTRAINT ck_mois_absences_statut CHECK
(
    statut
    IN
(
    'OUVERT',
    'CLOTURE'
))
    );

CREATE TABLE IF NOT EXISTS mois_absences_journal
(
    id
    BIGINT
    GENERATED
    ALWAYS AS
    IDENTITY
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    annee
    INTEGER
    NOT
    NULL,
    mois
    INTEGER
    NOT
    NULL,
    type_acte
    VARCHAR
(
    12
) NOT NULL,
    effectue_par UUID NOT NULL,
    effectue_le TIMESTAMP WITH TIME ZONE NOT NULL,
                              motif VARCHAR (500),
    a_qualifier_restantes BIGINT,
    en_retard BIGINT,
    CONSTRAINT ck_mois_absences_journal_type CHECK
(
    type_acte
    IN
(
    'CLOTURE',
    'REOUVERTURE'
))
    );

CREATE INDEX IF NOT EXISTS idx_mois_absences_journal_mois
    ON mois_absences_journal (center_id, annee, mois, id);
