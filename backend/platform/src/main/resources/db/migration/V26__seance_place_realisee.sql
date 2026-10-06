-- Migration V26 : place (salle, créneau, générateur) où une séance a eu lieu, figée à sa validation.
--
-- Le planning de la semaine retrouve ainsi une séance réalisée même si la place ou les jours de dialyse du patient
-- changent ensuite. Nulles pour les séances validées avant cette migration (le planning se rabat alors sur la place
-- actuelle du patient). Hibernate (ddl-auto: update) ajoute ces colonnes au démarrage ; ce script documente le schéma
-- (idempotent).

ALTER TABLE seances
    ADD COLUMN IF NOT EXISTS salle_id UUID;
ALTER TABLE seances
    ADD COLUMN IF NOT EXISTS creneau_id UUID;
ALTER TABLE seances
    ADD COLUMN IF NOT EXISTS generateur_id UUID;
