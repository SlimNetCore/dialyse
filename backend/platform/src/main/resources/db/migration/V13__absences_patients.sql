-- Migration V13 : suivi et valorisation des absences de patients.
--
-- Table sans entité JPA (créée aussi par db/schema.sql). Une absence par patient et par jour ; la valeur (forfait de la
-- prise en charge en TTC, taux de TVA du type HEMODIALYSE à la date, montant HT) est figée à la création.
-- Statuts : A_QUALIFIER, JUSTIFIEE, NON_JUSTIFIEE, RATTRAPEE, ANNULEE.

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
