-- ═══════════════════════════════════════════════════════════════════
-- Dossier médical — Phase 4 : administrations réelles du traitement de l'anémie (bc-medical)
-- NOTE : Flyway n'est pas actif sur ce projet (voir AGENTS.md / db/schema.sql) ; ce script
-- documente le schéma pour la parité PostgreSQL. Le schéma réel en dev/test est généré par
-- Hibernate (ddl-auto: update) à partir des @Entity.
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS administrations_anemie
(
    id                       UUID PRIMARY KEY,
    patient_id               UUID                     NOT NULL,
    center_id                UUID                     NOT NULL,
    prescription_medicale_id UUID,
    type_traitement          VARCHAR(20)              NOT NULL,
    molecule                 VARCHAR(100),
    dose                     NUMERIC(10, 3),
    unite_dose               VARCHAR(20),
    voie                     VARCHAR(10),
    date_administration      DATE                     NOT NULL,
    seance_id                UUID,
    administre_par           VARCHAR(100),
    administree              BOOLEAN                  NOT NULL DEFAULT TRUE,
    motif_non_administration VARCHAR(500),
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_administration_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE,
    CONSTRAINT fk_administration_prescription FOREIGN KEY (prescription_medicale_id)
        REFERENCES prescriptions_medicales (id) ON DELETE SET NULL,
    CONSTRAINT fk_administration_seance FOREIGN KEY (seance_id) REFERENCES seances (id) ON DELETE SET NULL,
    CONSTRAINT chk_administration_type CHECK (type_traitement IN ('EPO', 'FER_INJECTABLE')),
    CONSTRAINT chk_administration_dose CHECK (NOT administree OR dose IS NOT NULL),
    CONSTRAINT chk_administration_motif CHECK (administree OR motif_non_administration IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_administrations_patient ON administrations_anemie (center_id, patient_id);
CREATE INDEX IF NOT EXISTS idx_administrations_prescription ON administrations_anemie (prescription_medicale_id);
