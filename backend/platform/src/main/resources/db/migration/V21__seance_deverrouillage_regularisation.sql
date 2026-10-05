-- Migration V21 : déverrouillage d'une séance oubliée pour régularisation.
--
-- L'administrateur déverrouille une séance d'un jour passé restée « créée » ; l'infirmier peut alors la valider.
-- Colonnes nulles tant que la séance n'est pas déverrouillée (la table seances est aussi gérée par l'entité JPA).

ALTER TABLE seances
    ADD COLUMN IF NOT EXISTS regularisation_deverrouillee_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE seances
    ADD COLUMN IF NOT EXISTS regularisation_deverrouillee_by VARCHAR (100);
