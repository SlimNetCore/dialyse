-- Migration V15 : capacité d'une salle (nombre maximal de générateurs affectés).
--
-- Table sans entité JPA (voir aussi db/schema.sql). NULL = capacité illimitée. L'affectation d'un générateur à une salle
-- déjà à sa capacité est refusée par l'API GMAO.

ALTER TABLE salle
    ADD COLUMN IF NOT EXISTS capacite INTEGER;
