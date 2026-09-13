-- ═══════════════════════════════════════════════════════════════════
-- Dossier médical — Phase 2 : antécédents, allergies, sérologies (bc-medical)
-- NOTE : Flyway n'est pas actif sur ce projet (voir AGENTS.md / db/schema.sql) ;
-- ce script documente le schéma pour la parité PostgreSQL. Le schéma réel en
-- dev/test est généré par Hibernate (ddl-auto: update) à partir des @Entity.
-- ═══════════════════════════════════════════════════════════════════

-- 1. Antécédents et comorbidités (1-N avec patients)
CREATE TABLE IF NOT EXISTS antecedents_medicaux
(
    id                      UUID PRIMARY KEY,
    patient_id              UUID                     NOT NULL,
    center_id               UUID                     NOT NULL,
    type_antecedent         VARCHAR(20)              NOT NULL,
    diagnostic_code_system  VARCHAR(10),
    diagnostic_code         VARCHAR(20),
    diagnostic_code_display VARCHAR(255),
    libelle_libre           VARCHAR(255),
    date_debut              DATE                     NOT NULL,
    date_fin                DATE,
    statut_clinique         VARCHAR(20)              NOT NULL,
    severite                VARCHAR(50),
    note                    TEXT,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_antecedent_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE,
    CONSTRAINT chk_antecedent_type CHECK (type_antecedent IN
                                          ('MEDICAL', 'CHIRURGICAL', 'FAMILIAL', 'OBSTETRICAL', 'COMORBIDITE')),
    CONSTRAINT chk_antecedent_statut CHECK (statut_clinique IN ('ACTIF', 'RESOLU', 'INACTIF')),
    CONSTRAINT chk_antecedent_diagnostic CHECK (diagnostic_code IS NOT NULL OR libelle_libre IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_antecedents_patient ON antecedents_medicaux (center_id, patient_id);

-- 2. Allergies et intolérances (1-N avec patients)
CREATE TABLE IF NOT EXISTS allergies_patient
(
    id                     UUID PRIMARY KEY,
    patient_id             UUID                     NOT NULL,
    center_id              UUID                     NOT NULL,
    substance_code_system  VARCHAR(10),
    substance_code         VARCHAR(20)              NOT NULL,
    substance_code_display VARCHAR(255),
    categorie              VARCHAR(20)              NOT NULL,
    criticite              VARCHAR(10)              NOT NULL,
    type_reaction          VARCHAR(15)              NOT NULL,
    manifestations         TEXT,
    date_constatation      DATE                     NOT NULL,
    statut_verification    VARCHAR(15)              NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_allergie_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE,
    CONSTRAINT chk_allergie_categorie CHECK (categorie IN ('MEDICAMENT', 'ALIMENT', 'ENVIRONNEMENT', 'BIOLOGIQUE')),
    CONSTRAINT chk_allergie_criticite CHECK (criticite IN ('BASSE', 'HAUTE')),
    CONSTRAINT chk_allergie_type_reaction CHECK (type_reaction IN ('ALLERGIE', 'INTOLERANCE')),
    CONSTRAINT chk_allergie_statut CHECK (statut_verification IN ('SUSPECTEE', 'CONFIRMEE', 'REFUTEE')),
    CONSTRAINT chk_allergie_manifestations CHECK (criticite <> 'HAUTE' OR manifestations IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_allergies_patient ON allergies_patient (center_id, patient_id);
CREATE INDEX IF NOT EXISTS idx_allergies_criticite ON allergies_patient (center_id, patient_id, criticite);

-- 3. Sérologies (VIH, hépatites B/C, syphilis...) (1-N avec patients)
CREATE TABLE IF NOT EXISTS serologies_patient
(
    id                     UUID PRIMARY KEY,
    patient_id             UUID                     NOT NULL,
    center_id              UUID                     NOT NULL,
    marqueur               VARCHAR(15)              NOT NULL,
    resultat               VARCHAR(15)              NOT NULL,
    titre                  NUMERIC(10, 3),
    unite                  VARCHAR(20),
    date_prelevement       DATE                     NOT NULL,
    laboratoire            VARCHAR(255),
    date_prochain_controle DATE,
    conduite_a_tenir       TEXT,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_serologie_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE,
    CONSTRAINT chk_serologie_marqueur CHECK (marqueur IN
                                             ('VIH_AC', 'AG_HBS', 'AC_HBS', 'AC_HBC', 'AC_VHC', 'ARN_VHC', 'TPHA')),
    CONSTRAINT chk_serologie_resultat CHECK (resultat IN ('POSITIF', 'NEGATIF', 'DOUTEUX', 'EN_COURS')),
    CONSTRAINT chk_serologie_conduite CHECK (resultat <> 'POSITIF' OR conduite_a_tenir IS NOT NULL),
    CONSTRAINT uq_serologie_marqueur_date UNIQUE (patient_id, marqueur, date_prelevement)
);

CREATE INDEX IF NOT EXISTS idx_serologies_patient ON serologies_patient (center_id, patient_id);
