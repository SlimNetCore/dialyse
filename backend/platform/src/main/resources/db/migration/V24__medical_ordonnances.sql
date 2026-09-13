-- ═══════════════════════════════════════════════════════════════════
-- Dossier médical — Phase 5 : ordonnances médicamenteuses (bc-medical)
-- NOTE : Flyway n'est pas actif sur ce projet (voir AGENTS.md / db/schema.sql) ; ce script
-- documente le schéma pour la parité PostgreSQL. Le schéma réel en dev/test est généré par
-- Hibernate (ddl-auto: update) à partir des @Entity.
-- ═══════════════════════════════════════════════════════════════════

-- 1. Ordonnances (1-N avec patients)
CREATE TABLE IF NOT EXISTS ordonnances
(
    id                UUID PRIMARY KEY,
    patient_id        UUID                     NOT NULL,
    center_id         UUID                     NOT NULL,
    medecin_id        VARCHAR(100),
    date_prescription DATE                     NOT NULL,
    statut            VARCHAR(20)              NOT NULL,
    numero            VARCHAR(30),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    signed_at         TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_ordonnance_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE,
    CONSTRAINT chk_ordonnance_statut CHECK (statut IN ('BROUILLON', 'SIGNEE', 'IMPRIMEE', 'ANNULEE')),
    CONSTRAINT uq_ordonnance_numero_centre UNIQUE (center_id, numero)
);

CREATE INDEX IF NOT EXISTS idx_ordonnances_patient ON ordonnances (center_id, patient_id);

-- 2. Lignes d'une ordonnance (1-N avec ordonnances)
CREATE TABLE IF NOT EXISTS lignes_ordonnance
(
    id                      UUID PRIMARY KEY,
    ordonnance_id           UUID         NOT NULL,
    medicament_code_system  VARCHAR(10),
    medicament_code         VARCHAR(20),
    medicament_code_display VARCHAR(255),
    libelle                 VARCHAR(255),
    posologie               VARCHAR(255) NOT NULL,
    voie                    VARCHAR(50),
    duree_jours             INTEGER,
    quantite                INTEGER,
    instructions            VARCHAR(500),
    CONSTRAINT fk_ligne_ordonnance FOREIGN KEY (ordonnance_id) REFERENCES ordonnances (id) ON DELETE CASCADE,
    CONSTRAINT chk_ligne_ordonnance_contenu CHECK (medicament_code IS NOT NULL OR libelle IS NOT NULL),
    CONSTRAINT chk_ligne_ordonnance_duree CHECK (duree_jours IS NULL OR duree_jours > 0),
    CONSTRAINT chk_ligne_ordonnance_quantite CHECK (quantite IS NULL OR quantite > 0)
);

CREATE INDEX IF NOT EXISTS idx_lignes_ordonnance_ordonnance ON lignes_ordonnance (ordonnance_id);
