-- ═══════════════════════════════════════════════════════════════════
-- Dossier médical — Phase 3 : demandes d'examen et observations biologiques LOINC (bc-medical)
-- NOTE : Flyway n'est pas actif sur ce projet (voir AGENTS.md / db/schema.sql) ; ce script
-- documente le schéma pour la parité PostgreSQL. Le schéma réel en dev/test est généré par
-- Hibernate (ddl-auto: update) à partir des @Entity.
-- ═══════════════════════════════════════════════════════════════════

-- 1. Demandes d'examen (1-N avec patients)
CREATE TABLE IF NOT EXISTS demandes_examen
(
    id              UUID PRIMARY KEY,
    patient_id      UUID                     NOT NULL,
    center_id       UUID                     NOT NULL,
    prescripteur_id VARCHAR(100),
    date_demande    DATE                     NOT NULL,
    categorie       VARCHAR(15)              NOT NULL,
    urgent          BOOLEAN                  NOT NULL DEFAULT FALSE,
    motif           VARCHAR(500),
    statut          VARCHAR(20)              NOT NULL,
    conclusion      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_demande_examen_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE,
    CONSTRAINT chk_demande_examen_categorie CHECK (categorie IN ('BIOLOGIE', 'IMAGERIE', 'FONCTIONNEL', 'ANAPATH')),
    CONSTRAINT chk_demande_examen_statut CHECK (statut IN
                                                ('DEMANDE', 'PRELEVE', 'RESULTAT_DISPONIBLE', 'VALIDE', 'ANNULE'))
);

CREATE INDEX IF NOT EXISTS idx_demandes_examen_patient ON demandes_examen (center_id, patient_id);

-- 2. Lignes d'une demande d'examen (1-N avec demandes_examen)
CREATE TABLE IF NOT EXISTS lignes_demande_examen
(
    id                   UUID PRIMARY KEY,
    demande_id           UUID NOT NULL,
    analyte_code_system  VARCHAR(10),
    analyte_code         VARCHAR(20),
    analyte_code_display VARCHAR(255),
    libelle              VARCHAR(255),
    commentaire          VARCHAR(500),
    CONSTRAINT fk_ligne_demande_examen FOREIGN KEY (demande_id) REFERENCES demandes_examen (id) ON DELETE CASCADE,
    CONSTRAINT chk_ligne_demande_examen_contenu CHECK (analyte_code IS NOT NULL OR libelle IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_lignes_demande_examen_demande ON lignes_demande_examen (demande_id);

-- 3. Observations biologiques génériques codées LOINC (1-N avec patients)
CREATE TABLE IF NOT EXISTS observations_biologiques
(
    id                   UUID PRIMARY KEY,
    patient_id           UUID                     NOT NULL,
    center_id            UUID                     NOT NULL,
    demande_examen_id    UUID,
    analyte_code_system  VARCHAR(10)              NOT NULL,
    analyte_code         VARCHAR(20)              NOT NULL,
    analyte_code_display VARCHAR(255),
    valeur_num           NUMERIC(14, 4),
    unite                VARCHAR(20),
    valeur_texte         VARCHAR(500),
    date_prelevement     DATE                     NOT NULL,
    statut               VARCHAR(15)              NOT NULL,
    source               VARCHAR(20)              NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_observation_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE,
    CONSTRAINT fk_observation_demande_examen FOREIGN KEY (demande_examen_id) REFERENCES demandes_examen (id) ON DELETE SET NULL,
    CONSTRAINT chk_observation_statut CHECK (statut IN ('PRELIMINAIRE', 'FINAL', 'CORRIGE')),
    CONSTRAINT chk_observation_source CHECK (source IN ('SAISIE_DIRECTE', 'DERIVEE_BILAN', 'IMPORT')),
    CONSTRAINT chk_observation_valeur CHECK (
        (valeur_num IS NOT NULL AND valeur_texte IS NULL) OR (valeur_num IS NULL AND valeur_texte IS NOT NULL)
        )
);

CREATE INDEX IF NOT EXISTS idx_observations_patient ON observations_biologiques (center_id, patient_id);
CREATE INDEX IF NOT EXISTS idx_observations_demande ON observations_biologiques (demande_examen_id);
CREATE INDEX IF NOT EXISTS idx_observations_analyte ON observations_biologiques (center_id, patient_id, analyte_code);
