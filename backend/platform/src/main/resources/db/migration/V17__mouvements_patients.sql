-- Migration V17 : historique des mouvements de patients (admission, séjour temporaire, transfert, décès, greffe,
-- guérison, libération de la place).
--
-- Table sans entité JPA (créée aussi par db/schema.sql). Append-only : un mouvement n'est jamais modifié ni supprimé.
-- L'affectation (salle, créneau, générateur, jours) est celle que le patient occupait au moment du mouvement.

CREATE TABLE IF NOT EXISTS mouvement_patient
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
    type
    VARCHAR
(
    30
) NOT NULL,
    date_effet DATE NOT NULL,
    etat_precedent VARCHAR
(
    30
),
    etat_nouveau VARCHAR
(
    30
),
    salle_id UUID,
    position_id UUID,
    generateur_id UUID,
    jours_dialyse VARCHAR
(
    80
),
    automatique BOOLEAN NOT NULL DEFAULT FALSE,
    cree_le TIMESTAMP NOT NULL
    );
CREATE INDEX IF NOT EXISTS idx_mouvement_patient_centre ON mouvement_patient (center_id, date_effet);
CREATE INDEX IF NOT EXISTS idx_mouvement_patient_patient ON mouvement_patient (center_id, patient_id);
