-- Migration V27 : journal des séances supprimées par l'administrateur.
--
-- La séance et ses données liées (volets, administrations, sorties de stock restituées) sont effacées ; cette trace
-- conserve quelle séance (patient, date, statut), pourquoi (motif obligatoire), par qui et quand. Toujours bornée au
-- centre. Compatible H2 / PostgreSQL.

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
