-- V14: Cahier de dialyse complet
-- Extension volet_paramedical + nouvelles tables : releves, medicaments, articles, dossier médical,
-- abords vasculaires, prescriptions, resultats analyses

-- ═══════════════════════════════════════════════════════════════════
-- 1. Extension volet_paramedical avec tous les champs du plan
-- ═══════════════════════════════════════════════════════════════════

-- Pré-séance
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS poids_sec_cible_kg DECIMAL(5, 2);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS surcharge_hydrique_kg DECIMAL(5, 2);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS ta_systolique_avant SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS ta_diastolique_avant SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS fc_avant SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS temperature_avant DECIMAL(4, 1);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS etat_general_score SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS oedemes BOOLEAN DEFAULT FALSE;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS oedemes_localisation VARCHAR(255);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS dyspnee BOOLEAN DEFAULT FALSE;

-- Paramètres machine
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS qb_ml_min SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS qd_ml_min SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS uf_cible_ml INTEGER;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS duree_prevue_min SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS type_dialyseur VARCHAR(100);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS type_bain VARCHAR(30);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS conductivite DECIMAL(4, 2);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS temperature_bain DECIMAL(4, 1);

-- Anticoagulation
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS anticoag_type VARCHAR(20);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS anticoag_dose_initiale DECIMAL(8, 2);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS anticoag_dose_horaire DECIMAL(8, 2);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS nb_rincages SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS volume_rincage_ml INTEGER;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS heure_arret_heparine VARCHAR(10);

-- Abord vasculaire (à la séance)
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS abord_type VARCHAR(20);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS abord_cote VARCHAR(10);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS aiguille_calibre VARCHAR(20);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS ordre_ponction VARCHAR(20);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS aspect_site VARCHAR(30);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_ponction BOOLEAN DEFAULT FALSE;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_ponction_detail VARCHAR(500);

-- Post-séance
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS ta_systolique_apres SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS ta_diastolique_apres SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS fc_apres SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS kt_v_realise DECIMAL(4, 2);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS uf_reelle_ml INTEGER;

-- Incidents / complications
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_hypotension BOOLEAN DEFAULT FALSE;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_crampes BOOLEAN DEFAULT FALSE;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_cephalees BOOLEAN DEFAULT FALSE;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_frissons BOOLEAN DEFAULT FALSE;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_nausees BOOLEAN DEFAULT FALSE;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_thrombose BOOLEAN DEFAULT FALSE;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_autre VARCHAR(500);

-- Signature infirmier
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS signature_infirmier_id VARCHAR(100);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS signature_at TIMESTAMP WITH TIME ZONE;

-- ═══════════════════════════════════════════════════════════════════
-- 2. Relevés per-séance (surveillance toutes les 30-60 min)
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS releves_per_seance
(
    id                   UUID PRIMARY KEY,
    volet_paramedical_id UUID                     NOT NULL,
    center_id            UUID                     NOT NULL,
    heure_releve         VARCHAR(10)              NOT NULL,
    ta_systolique        SMALLINT,
    ta_diastolique       SMALLINT,
    fc                   SMALLINT,
    pression_veineuse    SMALLINT,
    pression_arterielle  SMALLINT,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_releve_volet FOREIGN KEY (volet_paramedical_id) REFERENCES volet_paramedical (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_releves_volet ON releves_per_seance (volet_paramedical_id);

-- ═══════════════════════════════════════════════════════════════════
-- 3. Médicaments injectés en fin de séance
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS medicaments_seance
(
    id                   UUID PRIMARY KEY,
    volet_paramedical_id UUID                     NOT NULL,
    center_id            UUID                     NOT NULL,
    nom_medicament       VARCHAR(255)             NOT NULL,
    dose                 VARCHAR(100),
    voie                 VARCHAR(50),
    heure_injection      VARCHAR(10),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_medicament_volet FOREIGN KEY (volet_paramedical_id) REFERENCES volet_paramedical (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_medicaments_volet ON medicaments_seance (volet_paramedical_id);

-- ═══════════════════════════════════════════════════════════════════
-- 4. Articles consommés à la séance (stocks décrementés à la validation)
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS articles_consommes_seance
(
    id                   UUID PRIMARY KEY,
    volet_paramedical_id UUID                     NOT NULL,
    center_id            UUID                     NOT NULL,
    article_id           UUID                     NOT NULL,
    quantite             DECIMAL(10, 3)           NOT NULL,
    lot                  VARCHAR(100),
    stock_mouvement_id   UUID,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_article_consomme_volet FOREIGN KEY (volet_paramedical_id) REFERENCES volet_paramedical (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_consomme_article FOREIGN KEY (article_id) REFERENCES articles (id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_articles_consommes_volet ON articles_consommes_seance (volet_paramedical_id);

-- ═══════════════════════════════════════════════════════════════════
-- 5. Dossier médical patient (1-1 avec patients, lié au patient, pas à la séance)
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS dossier_medical_patient
(
    id                    UUID PRIMARY KEY,
    patient_id            UUID                     NOT NULL UNIQUE,
    center_id             UUID                     NOT NULL,
    nephropathie_initiale VARCHAR(255),
    date_mise_en_dialyse  DATE,
    hepatite_b_statut     VARCHAR(20),
    hepatite_c_statut     VARCHAR(20),
    observation_globale   TEXT,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_dossier_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_dossier_medical_center ON dossier_medical_patient (center_id, patient_id);

-- ═══════════════════════════════════════════════════════════════════
-- 6. Historique abords vasculaires (1-N avec patients)
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS abords_vasculaires
(
    id            UUID PRIMARY KEY,
    patient_id    UUID                     NOT NULL,
    center_id     UUID                     NOT NULL,
    type_abord    VARCHAR(20)              NOT NULL,
    cote          VARCHAR(10),
    localisation  VARCHAR(255),
    date_creation DATE,
    date_fin      DATE,
    actif         BOOLEAN                  NOT NULL DEFAULT TRUE,
    complications VARCHAR(500),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_abord_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_abords_patient ON abords_vasculaires (center_id, patient_id);

-- ═══════════════════════════════════════════════════════════════════
-- 7. Prescriptions médicales datées (1-N avec patients)
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS prescriptions_medicales
(
    id                      UUID PRIMARY KEY,
    patient_id              UUID                     NOT NULL,
    center_id               UUID                     NOT NULL,
    date_prescription       DATE                     NOT NULL,
    medecin_id              VARCHAR(100),
    qb_cible                SMALLINT,
    qd_cible                SMALLINT,
    uf_max_ml               INTEGER,
    duree_cible_min         SMALLINT,
    type_dialyseur_prescrit VARCHAR(100),
    anticoag_type_prescrit  VARCHAR(20),
    epo_molecule            VARCHAR(100),
    epo_dose_ui             INTEGER,
    epo_voie                VARCHAR(10),
    epo_frequence           VARCHAR(50),
    fer_molecule            VARCHAR(100),
    fer_dose_mg             INTEGER,
    fer_voie                VARCHAR(10),
    fer_frequence           VARCHAR(50),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_prescription_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_prescriptions_patient ON prescriptions_medicales (center_id, patient_id, date_prescription);

-- ═══════════════════════════════════════════════════════════════════
-- 8. Autres traitements d'une prescription (1-N avec prescriptions)
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS autres_traitements_prescription
(
    id              UUID PRIMARY KEY,
    prescription_id UUID                     NOT NULL,
    nom_medicament  VARCHAR(255)             NOT NULL,
    dose            VARCHAR(100),
    voie            VARCHAR(50),
    frequence       VARCHAR(50),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_autre_traitement_prescription FOREIGN KEY (prescription_id) REFERENCES prescriptions_medicales (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_autres_traitements_prescription ON autres_traitements_prescription (prescription_id);

-- ═══════════════════════════════════════════════════════════════════
-- 9. Résultats d'analyses biologiques (1-N avec patients)
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS resultats_analyses
(
    id                  UUID PRIMARY KEY,
    patient_id          UUID                     NOT NULL,
    center_id           UUID                     NOT NULL,
    date_prelevement    DATE                     NOT NULL,
    -- NFS
    hb_g_dl             DECIMAL(4, 1),
    ht_pct              DECIMAL(5, 2),
    plaquettes          INTEGER,
    -- Bilan martial
    ferritine_ng_ml     DECIMAL(8, 1),
    cstf_pct            DECIMAL(5, 2),
    epo_endogene_mui_ml DECIMAL(8, 2),
    -- Dialyse adéquation
    uree_pre_mg_dl      DECIMAL(7, 2),
    uree_post_mg_dl     DECIMAL(7, 2),
    creatinine_mg_dl    DECIMAL(7, 2),
    kt_v_mensuel        DECIMAL(4, 2),
    -- Bilan phospho-calcique
    phosphore_mg_dl     DECIMAL(6, 2),
    calcium_mg_dl       DECIMAL(6, 2),
    pth_pg_ml           DECIMAL(8, 1),
    -- Nutritionnel / inflammatoire
    albumine_g_dl       DECIMAL(4, 1),
    proteines_g_dl      DECIMAL(4, 1),
    crp_mg_l            DECIMAL(7, 2),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_analyses_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_analyses_patient ON resultats_analyses (center_id, patient_id, date_prelevement);

