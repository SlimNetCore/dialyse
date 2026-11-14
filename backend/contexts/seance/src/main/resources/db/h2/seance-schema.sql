-- DDL H2 (tests et poste de développement) des tables dont la Séance est propriétaire (S-DDL3b, ADR 0187).
-- Chargé par spring.sql.init.schema-locations (application.yml de la plateforme) ; en production le schéma vient de Flyway.
-- Séances et volets (contexte Séance, SE7f, ADR 0100) : jusqu'à SE7f ces tables H2 étaient générées par les entités JPA de la plateforme
-- (retirées avec bc-seance) : mêmes colonnes, mêmes types et mêmes contraintes que ces entités (et que Flyway V1, V21, V23, V26, V39
-- pour seances ; les colonnes de facturation V39-V41 sont déclarées ici dès le CREATE).

CREATE TABLE IF NOT EXISTS seances
(
    id                              UUID PRIMARY KEY,
    patient_id                      UUID         NOT NULL,
    center_id                       UUID         NOT NULL,
    date_seance                     DATE         NOT NULL,
    statut                          VARCHAR(255) NOT NULL,
    created_at                      TIMESTAMP WITH TIME ZONE,
    validated_at                    TIMESTAMP WITH TIME ZONE,
    signed_infirmier_at             TIMESTAMP WITH TIME ZONE,
    signed_infirmier_by             VARCHAR(100),
    signed_medecin_at               TIMESTAMP WITH TIME ZONE,
    signed_medecin_by               VARCHAR(100),
    facture_id                      UUID,
    forfait_override_id             UUID,
    forfait_override_code           VARCHAR(50),
    forfait_override_nom            VARCHAR(255),
    forfait_override_prix           NUMERIC(14, 2),
    forfait_override_updated_at     TIMESTAMP WITH TIME ZONE,
    forfait_override_updated_by     VARCHAR(100),
    regularisation_deverrouillee_at TIMESTAMP WITH TIME ZONE,
    regularisation_deverrouillee_by VARCHAR(100),
    hors_planning                   BOOLEAN DEFAULT FALSE NOT NULL,
    motif_hors_planning             VARCHAR(20),
    precision_hors_planning         VARCHAR(255),
    salle_id                        UUID,
    creneau_id                      UUID,
    generateur_id                   UUID,
    statut_avant_exclusion          VARCHAR(20),
    exclusion_absence_id            UUID
);
CREATE INDEX IF NOT EXISTS idx_seances_center_date ON seances (center_id, date_seance);
CREATE INDEX IF NOT EXISTS idx_seances_center_patient_date ON seances (center_id, patient_id, date_seance);

CREATE TABLE IF NOT EXISTS volet_paramedical
(
    id                 UUID PRIMARY KEY,
    seance_id          UUID NOT NULL UNIQUE,
    center_id          UUID NOT NULL,
    poids_avant_kg     NUMERIC(38, 2),
    poids_apres_kg     NUMERIC(38, 2),
    ta_avant           VARCHAR(30),
    ta_apres           VARCHAR(30),
    duree_minutes      INTEGER,
    debit_sang_ml_min  INTEGER,
    ultrafiltration_ml NUMERIC(38, 2),
    anticoagulant      VARCHAR(100),
    type_dialysat      VARCHAR(100),
    incidents          TEXT,
    created_at         TIMESTAMP WITH TIME ZONE,
    updated_at         TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS volet_medical
(
    id                         UUID PRIMARY KEY,
    seance_id                  UUID NOT NULL UNIQUE,
    center_id                  UUID NOT NULL,
    prescription               TEXT,
    tolerance_seance           TEXT,
    examen_clinique            TEXT,
    resultats_biologiques      TEXT,
    ajustements_therapeutiques TEXT,
    conclusion_medicale        TEXT,
    created_at                 TIMESTAMP WITH TIME ZONE,
    updated_at                 TIMESTAMP WITH TIME ZONE
);

-- Consommables proposés en un toucher à l'infirmier (poste infirmier), par centre et dans l'ordre choisi.
CREATE TABLE IF NOT EXISTS seance_raccourcis_articles
(
    center_id UUID NOT NULL,
    article_id UUID NOT NULL,
    ordre INTEGER NOT NULL,
    PRIMARY KEY (center_id, article_id)
);

-- Journal des séances supprimées (voir migration V27)
CREATE TABLE IF NOT EXISTS seance_suppression
(
    id          UUID PRIMARY KEY,
    center_id   UUID                     NOT NULL,
    seance_id   UUID                     NOT NULL,
    patient_id  UUID                     NOT NULL,
    date_seance DATE                     NOT NULL,
    statut      VARCHAR(20)              NOT NULL,
    motif       VARCHAR(500)             NOT NULL,
    supprime_par VARCHAR(255)            NOT NULL,
    supprime_le TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_seance_suppression_centre ON seance_suppression (center_id, supprime_le);
