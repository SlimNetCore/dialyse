-- V15: Phase 1 alignment for dialysis log schema
-- Goal: align SQL types and integrity constraints with the execution plan while staying backward-compatible.

-- 1) Align time-based fields that were initially stored as VARCHAR.
ALTER TABLE volet_paramedical
    ALTER COLUMN heure_arret_heparine TYPE TIME
    USING CASE
        WHEN heure_arret_heparine IS NULL OR TRIM(heure_arret_heparine) = '' THEN NULL
        WHEN heure_arret_heparine ~ '^[0-2][0-9]:[0-5][0-9](:[0-5][0-9])?$' THEN heure_arret_heparine::TIME
        ELSE NULL
    END;

ALTER TABLE releves_per_seance
    ALTER COLUMN heure_releve TYPE TIME
    USING CASE
        WHEN heure_releve IS NULL OR TRIM(heure_releve) = '' THEN NULL
        WHEN heure_releve ~ '^[0-2][0-9]:[0-5][0-9](:[0-5][0-9])?$' THEN heure_releve::TIME
        ELSE NULL
    END;

ALTER TABLE medicaments_seance
    ALTER COLUMN heure_injection TYPE TIME
    USING CASE
        WHEN heure_injection IS NULL OR TRIM(heure_injection) = '' THEN NULL
        WHEN heure_injection ~ '^[0-2][0-9]:[0-5][0-9](:[0-5][0-9])?$' THEN heure_injection::TIME
        ELSE NULL
    END;

-- 2) Align user identifiers that should be UUID references.
ALTER TABLE volet_paramedical
    ALTER COLUMN signature_infirmier_id TYPE UUID
    USING CASE
        WHEN signature_infirmier_id IS NULL OR TRIM(signature_infirmier_id) = '' THEN NULL
        ELSE signature_infirmier_id::UUID
    END;

ALTER TABLE prescriptions_medicales
    ALTER COLUMN medecin_id TYPE UUID
    USING CASE
        WHEN medecin_id IS NULL OR TRIM(medecin_id) = '' THEN NULL
        ELSE medecin_id::UUID
    END;

-- 3) Align numeric precision/scale with the business contract.
ALTER TABLE articles_consommes_seance
    ALTER COLUMN quantite TYPE DECIMAL(8,2);

ALTER TABLE volet_paramedical
    ALTER COLUMN uf_cible_ml TYPE SMALLINT USING uf_cible_ml::SMALLINT,
ALTER
COLUMN uf_reelle_ml TYPE SMALLINT USING uf_reelle_ml::SMALLINT,
    ALTER
COLUMN volume_rincage_ml TYPE SMALLINT USING volume_rincage_ml::SMALLINT;

-- 4) Add missing foreign keys expected by the model.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_volet_signature_infirmier'
    ) THEN
        ALTER TABLE volet_paramedical
            ADD CONSTRAINT fk_volet_signature_infirmier
            FOREIGN KEY (signature_infirmier_id)
            REFERENCES app_user (id)
            ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_prescription_medecin'
    ) THEN
        ALTER TABLE prescriptions_medicales
            ADD CONSTRAINT fk_prescription_medecin
            FOREIGN KEY (medecin_id)
            REFERENCES app_user (id)
            ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_article_consomme_stock_mouvement'
    ) THEN
        ALTER TABLE articles_consommes_seance
            ADD CONSTRAINT fk_article_consomme_stock_mouvement
            FOREIGN KEY (stock_mouvement_id)
            REFERENCES stock_mouvements (id)
            ON DELETE SET NULL;
    END IF;
END
$$;

-- 5) Add enum-like checks to protect data quality.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_volet_type_bain'
    ) THEN
        ALTER TABLE volet_paramedical
            ADD CONSTRAINT ck_volet_type_bain
            CHECK (type_bain IS NULL OR type_bain IN ('BICARBONATE_STD', 'PERSONNALISE'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_volet_anticoag_type'
    ) THEN
        ALTER TABLE volet_paramedical
            ADD CONSTRAINT ck_volet_anticoag_type
            CHECK (anticoag_type IS NULL OR anticoag_type IN ('HNF', 'HBPM', 'CITRATE', 'AUCUN'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_volet_abord_type'
    ) THEN
        ALTER TABLE volet_paramedical
            ADD CONSTRAINT ck_volet_abord_type
            CHECK (abord_type IS NULL OR abord_type IN ('FAV', 'PTFE', 'KT_TUNNELISE', 'KT_AIGU'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_volet_abord_cote'
    ) THEN
        ALTER TABLE volet_paramedical
            ADD CONSTRAINT ck_volet_abord_cote
            CHECK (abord_cote IS NULL OR abord_cote IN ('GAUCHE', 'DROIT'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_volet_ordre_ponction'
    ) THEN
        ALTER TABLE volet_paramedical
            ADD CONSTRAINT ck_volet_ordre_ponction
            CHECK (ordre_ponction IS NULL OR ordre_ponction IN ('ANTEROGRADE', 'RETROGRADE', 'BUTTONHOLE'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_volet_aspect_site'
    ) THEN
        ALTER TABLE volet_paramedical
            ADD CONSTRAINT ck_volet_aspect_site
            CHECK (aspect_site IS NULL OR aspect_site IN ('BON', 'DIFFICILE', 'SUINTEMENT', 'HEMATOME', 'AUTRE'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_dossier_hepatite_b'
    ) THEN
        ALTER TABLE dossier_medical_patient
            ADD CONSTRAINT ck_dossier_hepatite_b
            CHECK (hepatite_b_statut IS NULL OR hepatite_b_statut IN ('NEGATIF', 'PORTEUR', 'VACCINE', 'IMMUNE', 'INCONNU'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_dossier_hepatite_c'
    ) THEN
        ALTER TABLE dossier_medical_patient
            ADD CONSTRAINT ck_dossier_hepatite_c
            CHECK (hepatite_c_statut IS NULL OR hepatite_c_statut IN ('NEGATIF', 'POSITIF', 'TRAITE', 'INCONNU'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_prescription_anticoag_type'
    ) THEN
        ALTER TABLE prescriptions_medicales
            ADD CONSTRAINT ck_prescription_anticoag_type
            CHECK (anticoag_type_prescrit IS NULL OR anticoag_type_prescrit IN ('HNF', 'HBPM', 'CITRATE', 'AUCUN'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_prescription_epo_voie'
    ) THEN
        ALTER TABLE prescriptions_medicales
            ADD CONSTRAINT ck_prescription_epo_voie
            CHECK (epo_voie IS NULL OR epo_voie IN ('SC', 'IV'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_prescription_fer_voie'
    ) THEN
        ALTER TABLE prescriptions_medicales
            ADD CONSTRAINT ck_prescription_fer_voie
            CHECK (fer_voie IS NULL OR fer_voie IN ('IV'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_abords_type'
    ) THEN
        ALTER TABLE abords_vasculaires
            ADD CONSTRAINT ck_abords_type
            CHECK (type_abord IN ('FAV', 'PTFE', 'KT_TUNNELISE', 'KT_AIGU'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_abords_cote'
    ) THEN
        ALTER TABLE abords_vasculaires
            ADD CONSTRAINT ck_abords_cote
            CHECK (cote IS NULL OR cote IN ('GAUCHE', 'DROIT'));
    END IF;
END
$$;

-- 6) Improve lookup performance for patient-level medical timeline queries.
CREATE INDEX IF NOT EXISTS idx_abords_patient_date_creation
    ON abords_vasculaires (patient_id, date_creation DESC);

CREATE INDEX IF NOT EXISTS idx_prescriptions_patient_date
    ON prescriptions_medicales (patient_id, date_prescription DESC);

CREATE INDEX IF NOT EXISTS idx_analyses_patient_date
    ON resultats_analyses (patient_id, date_prelevement DESC);

