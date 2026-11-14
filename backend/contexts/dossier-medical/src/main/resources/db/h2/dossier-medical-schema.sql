-- DDL H2 (tests et poste de développement) des tables dont le Dossier médical est propriétaire (S-DDL3b, ADR 0187).
-- Chargé par spring.sql.init.schema-locations (application.yml de la plateforme) ; en production le schéma vient de Flyway.
-- Résultats d'analyses (SE7f) et les 17 tables du contexte (DM9, ADR 0101) : jusqu'à DM9 ces tables H2 étaient générées par les entités
-- JPA de la plateforme (retirées avec bc-medical) : colonnes, types et clés primaires de Flyway V1 ; poids sec et sa contrainte de V2 ;
-- index de V31 ; colonnes de détail des alertes de V53. Écart connu : dossier_medical_patient porte l'unicité GLOBALE de patient_id
-- dans V1 comme dans l'entité retirée (identiques, ADR 0087).

CREATE TABLE IF NOT EXISTS resultats_analyses
(
    id                  UUID PRIMARY KEY,
    patient_id          UUID NOT NULL,
    center_id           UUID NOT NULL,
    date_prelevement    DATE NOT NULL,
    hb_g_dl             NUMERIC(38, 2),
    ht_pct              NUMERIC(38, 2),
    plaquettes          INTEGER,
    ferritine_ng_ml     NUMERIC(38, 2),
    cstf_pct            NUMERIC(38, 2),
    epo_endogene_mui_ml NUMERIC(38, 2),
    uree_pre_mg_dl      NUMERIC(38, 2),
    uree_post_mg_dl     NUMERIC(38, 2),
    creatinine_mg_dl    NUMERIC(38, 2),
    kt_v_mensuel        NUMERIC(38, 2),
    phosphore_mg_dl     NUMERIC(38, 2),
    calcium_mg_dl       NUMERIC(38, 2),
    pth_pg_ml           NUMERIC(38, 2),
    albumine_g_dl       NUMERIC(38, 2),
    proteines_g_dl      NUMERIC(38, 2),
    crp_mg_l            NUMERIC(38, 2),
    created_at          TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_resultats_patient_date ON resultats_analyses (patient_id, date_prelevement);

CREATE TABLE IF NOT EXISTS abords_vasculaires
(
    id            UUID PRIMARY KEY,
    actif         BOOLEAN     NOT NULL,
    center_id     UUID        NOT NULL,
    complications VARCHAR(500),
    cote          VARCHAR(10),
    created_at    TIMESTAMP WITH TIME ZONE,
    date_creation DATE,
    date_fin      DATE,
    localisation  VARCHAR(255),
    patient_id    UUID        NOT NULL,
    type_abord    VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS administrations_anemie
(
    id                       UUID PRIMARY KEY,
    administre_par           VARCHAR(100),
    administree              BOOLEAN                  NOT NULL,
    article_id               UUID,
    center_id                UUID                     NOT NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    date_administration      DATE                     NOT NULL,
    dose                     NUMERIC(10, 3),
    molecule                 VARCHAR(100),
    motif_non_administration VARCHAR(500),
    patient_id               UUID                     NOT NULL,
    prescription_medicale_id UUID,
    quantite_article         NUMERIC(10, 3),
    seance_id                UUID,
    type_traitement          VARCHAR(20)              NOT NULL,
    unite_dose               VARCHAR(20),
    voie                     VARCHAR(10)
);
CREATE INDEX IF NOT EXISTS idx_admin_anemie_patient ON administrations_anemie (center_id, patient_id, date_administration);

CREATE TABLE IF NOT EXISTS alertes_observance
(
    id                 UUID PRIMARY KEY,
    center_id          UUID                     NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    doses_administrees INTEGER                  NOT NULL,
    doses_attendues    INTEGER                  NOT NULL,
    message            VARCHAR(500),
    patient_id         UUID                     NOT NULL,
    periode_debut      DATE                     NOT NULL,
    periode_fin        DATE                     NOT NULL,
    resolved_at        TIMESTAMP WITH TIME ZONE,
    type_alerte        VARCHAR(20)              NOT NULL,
    type_traitement    VARCHAR(20)              NOT NULL,
    unite_dose         VARCHAR(10),
    dose_prescrite     INTEGER,
    frequence_valeur   INTEGER,
    frequence_unite    VARCHAR(10)
);

CREATE TABLE IF NOT EXISTS allergies_patient
(
    id                     UUID PRIMARY KEY,
    categorie              VARCHAR(20)              NOT NULL,
    center_id              UUID                     NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    criticite              VARCHAR(10)              NOT NULL,
    date_constatation      DATE                     NOT NULL,
    manifestations         TEXT,
    patient_id             UUID                     NOT NULL,
    statut_verification    VARCHAR(15)              NOT NULL,
    substance_code         VARCHAR(20)              NOT NULL,
    substance_code_display VARCHAR(255),
    substance_code_system  VARCHAR(10),
    type_reaction          VARCHAR(15)              NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS antecedents_medicaux
(
    id                      UUID PRIMARY KEY,
    center_id               UUID                     NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    date_debut              DATE                     NOT NULL,
    date_fin                DATE,
    diagnostic_code         VARCHAR(20),
    diagnostic_code_display VARCHAR(255),
    diagnostic_code_system  VARCHAR(10),
    libelle_libre           VARCHAR(255),
    note                    TEXT,
    patient_id              UUID                     NOT NULL,
    severite                VARCHAR(50),
    statut_clinique         VARCHAR(20)              NOT NULL,
    type_antecedent         VARCHAR(20)              NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS bilans_pre_greffe
(
    id                             UUID PRIMARY KEY,
    center_id                      UUID                     NOT NULL,
    conclusion_nephrologue         TEXT,
    contre_indications             TEXT,
    created_at                     TIMESTAMP WITH TIME ZONE NOT NULL,
    date_debut_bilan               DATE,
    date_greffe                    DATE,
    date_inscription_liste_attente DATE,
    groupe_sanguin_confirme        VARCHAR(5),
    patient_id                     UUID                     NOT NULL,
    pra_classe_i                   NUMERIC(5, 2),
    pra_classe_ii                  NUMERIC(5, 2),
    statut                         VARCHAR(30)              NOT NULL,
    typage_hla                     VARCHAR(500),
    updated_at                     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS decisions_rcp
(
    id                   UUID PRIMARY KEY,
    avis                 VARCHAR(15) NOT NULL,
    bilan_id             UUID        NOT NULL,
    compte_rendu         TEXT,
    date_reunion         DATE        NOT NULL,
    prochaine_date_revue DATE
);

CREATE TABLE IF NOT EXISTS demandes_examen
(
    id              UUID PRIMARY KEY,
    categorie       VARCHAR(15)              NOT NULL,
    center_id       UUID                     NOT NULL,
    conclusion      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    date_demande    DATE                     NOT NULL,
    motif           VARCHAR(500),
    patient_id      UUID                     NOT NULL,
    prescripteur_id VARCHAR(100),
    statut          VARCHAR(20)              NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    urgent          BOOLEAN                  NOT NULL
);

CREATE TABLE IF NOT EXISTS donneurs_vivants
(
    id                  UUID PRIMARY KEY,
    bilan_realise       TEXT,
    center_id           UUID                     NOT NULL,
    contre_indications  TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    crossmatch_resultat VARCHAR(15)              NOT NULL,
    date_crossmatch     DATE,
    date_decision       DATE,
    date_naissance      DATE,
    decision_finale     TEXT,
    groupe_sanguin      VARCHAR(5),
    lien_parente        VARCHAR(20)              NOT NULL,
    nom                 VARCHAR(100)             NOT NULL,
    patient_id          UUID                     NOT NULL,
    prenom              VARCHAR(100),
    statut_bilan        VARCHAR(20)              NOT NULL,
    telephone           VARCHAR(30),
    typage_hla          VARCHAR(500),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS dossier_medical_patient
(
    id                    UUID PRIMARY KEY,
    center_id             UUID NOT NULL,
    conclusion_medicale   TEXT,
    created_at            TIMESTAMP WITH TIME ZONE,
    date_mise_en_dialyse  DATE,
    hepatite_b_statut     VARCHAR(20),
    hepatite_c_statut     VARCHAR(20),
    nephropathie_initiale VARCHAR(255),
    observation_globale   TEXT,
    patient_id            UUID NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk5obbasyg8v5vdwb90d6lotno2 UNIQUE (patient_id)
);

CREATE TABLE IF NOT EXISTS etapes_bilan_pre_greffe
(
    id                UUID PRIMARY KEY,
    categorie         VARCHAR(20)              NOT NULL,
    center_id         UUID                     NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    date_expiration   DATE,
    date_realisation  DATE,
    demande_examen_id UUID,
    libelle           VARCHAR(255)             NOT NULL,
    patient_id        UUID                     NOT NULL,
    resultat          TEXT,
    serologie_id      UUID,
    statut            VARCHAR(20)              NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS lignes_demande_examen
(
    id                   UUID PRIMARY KEY,
    analyte_code         VARCHAR(20),
    analyte_code_display VARCHAR(255),
    analyte_code_system  VARCHAR(10),
    commentaire          VARCHAR(500),
    demande_id           UUID NOT NULL,
    libelle              VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS lignes_ordonnance
(
    id                      UUID PRIMARY KEY,
    duree_jours             INTEGER,
    instructions            VARCHAR(500),
    libelle                 VARCHAR(255),
    medicament_code         VARCHAR(20),
    medicament_code_display VARCHAR(255),
    medicament_code_system  VARCHAR(10),
    ordonnance_id           UUID         NOT NULL,
    posologie               VARCHAR(255) NOT NULL,
    quantite                INTEGER,
    voie                    VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS observations_biologiques
(
    id                   UUID PRIMARY KEY,
    analyte_code         VARCHAR(20)              NOT NULL,
    analyte_code_display VARCHAR(255),
    analyte_code_system  VARCHAR(10)              NOT NULL,
    center_id            UUID                     NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    date_prelevement     DATE                     NOT NULL,
    demande_examen_id    UUID,
    patient_id           UUID                     NOT NULL,
    source               VARCHAR(20)              NOT NULL,
    statut               VARCHAR(15)              NOT NULL,
    unite                VARCHAR(20),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    valeur_num           NUMERIC(14, 4),
    valeur_texte         VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_obs_bio_patient_date ON observations_biologiques (center_id, patient_id, date_prelevement);

CREATE TABLE IF NOT EXISTS ordonnances
(
    id                UUID PRIMARY KEY,
    center_id         UUID                     NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    date_prescription DATE                     NOT NULL,
    medecin_id        VARCHAR(100),
    numero            VARCHAR(30),
    patient_id        UUID                     NOT NULL,
    signed_at         TIMESTAMP WITH TIME ZONE,
    statut            VARCHAR(20)              NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS prescriptions_medicales
(
    id                      UUID PRIMARY KEY,
    anticoag_type_prescrit  VARCHAR(20),
    center_id               UUID NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE,
    date_prescription       DATE NOT NULL,
    duree_cible_min         INTEGER,
    epo_article_id          UUID,
    epo_dose_ui             INTEGER,
    epo_frequence_unite     VARCHAR(10),
    epo_frequence_valeur    INTEGER,
    epo_voie                VARCHAR(10),
    fer_article_id          UUID,
    fer_dose_mg             INTEGER,
    fer_frequence_unite     VARCHAR(10),
    fer_frequence_valeur    INTEGER,
    fer_voie                VARCHAR(10),
    medecin_id              UUID,
    patient_id              UUID NOT NULL,
    qb_cible                INTEGER,
    qd_cible                INTEGER,
    type_dialyseur_prescrit VARCHAR(100),
    uf_max_ml               INTEGER,
    updated_at              TIMESTAMP WITH TIME ZONE,
    poids_sec_cible_kg      NUMERIC(5, 2),
    CONSTRAINT ck_prescription_poids_sec_cible CHECK (poids_sec_cible_kg IS NULL OR poids_sec_cible_kg BETWEEN 20 AND 300)
);
CREATE INDEX IF NOT EXISTS idx_prescriptions_patient ON prescriptions_medicales (center_id, patient_id, date_prescription);

CREATE TABLE IF NOT EXISTS serologies_patient
(
    id                     UUID PRIMARY KEY,
    center_id              UUID                     NOT NULL,
    conduite_a_tenir       TEXT,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    date_prelevement       DATE                     NOT NULL,
    date_prochain_controle DATE,
    laboratoire            VARCHAR(255),
    marqueur               VARCHAR(15)              NOT NULL,
    patient_id             UUID                     NOT NULL,
    resultat               VARCHAR(15)              NOT NULL,
    titre                  NUMERIC(10, 3),
    unite                  VARCHAR(20),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL
);
