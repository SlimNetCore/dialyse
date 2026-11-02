-- Migration V22 : agrégat satellite DerogationRegularisation (slice SE4).
CREATE TABLE IF NOT EXISTS derogations_regularisation
(
    id
    UUID
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    seance_id
    UUID
    NOT
    NULL,
    motif
    VARCHAR
(
    500
) NOT NULL,
    autorite VARCHAR
(
    100
) NOT NULL,
    date_derogation TIMESTAMP WITH TIME ZONE NOT NULL
                                  );
CREATE INDEX IF NOT EXISTS idx_derogations_regularisation_centre ON derogations_regularisation (center_id);
CREATE INDEX IF NOT EXISTS idx_derogations_regularisation_seance ON derogations_regularisation (seance_id, center_id);