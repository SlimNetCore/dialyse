-- V17: Articles managed-by-lot flag
-- Existing data keeps lot behavior by default.

ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS gere_par_lot BOOLEAN;

UPDATE articles
SET gere_par_lot = TRUE
WHERE gere_par_lot IS NULL;

ALTER TABLE articles
    ALTER COLUMN gere_par_lot SET DEFAULT TRUE;

ALTER TABLE articles
    ALTER COLUMN gere_par_lot SET NOT NULL;

