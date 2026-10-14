-- Migration V36 : tâches d'intervention GMAO (fonctionnalité nouvelle, KB-GMAO-55, demandée le 2026-10-12).
--
-- ADDITIVE : aucun DROP, aucun DELETE. La table gmao_taches_intervention existe déjà depuis V5 (id, intervention_id,
-- description, statut, date_creation, date_completion, clé étrangère vers gmao_interventions avec ON DELETE CASCADE), mais
-- aucun code ne l'a jamais écrite ni lue (l'ancienne TacheIntervention n'avait pas d'entité JPA, KB-GMAO-55) : elle est vide
-- en pratique. Cette migration ajoute seulement les colonnes manquantes ; les colonnes de V5 date_creation (valeur par défaut
-- à l'insertion) et date_completion (jamais renseignée) restent en place, inutilisées par le nouveau code : leur retrait est
-- une décision ultérieure.
--
-- Tâches d'une intervention : statut A_FAIRE, EN_COURS ou COMPLETEE (à valider par le métier), au plus 30 par intervention
-- (règle du domaine, pas de la base). 'rang' garde l'ordre d'ajout ; centre_id duplique celui de l'intervention pour borner
-- toute lecture au centre. Les lignes éventuelles (attendues : aucune) sont complétées à partir de l'intervention.

ALTER TABLE gmao_taches_intervention
    ADD COLUMN IF NOT EXISTS centre_id   UUID,
    ADD COLUMN IF NOT EXISTS rang        INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cree_le     TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS cree_par    UUID,
    ADD COLUMN IF NOT EXISTS demarre_le  TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS demarre_par UUID,
    ADD COLUMN IF NOT EXISTS acheve_le   TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS acheve_par  UUID;

UPDATE gmao_taches_intervention t
SET centre_id = i.centre_id
FROM gmao_interventions i
WHERE i.id = t.intervention_id
  AND t.centre_id IS NULL;

UPDATE gmao_taches_intervention
SET cree_le = date_creation AT TIME ZONE 'UTC'
WHERE cree_le IS NULL;

ALTER TABLE gmao_taches_intervention
    ALTER COLUMN centre_id SET NOT NULL,
    ALTER COLUMN cree_le SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_gmao_taches_intervention_rang ON gmao_taches_intervention (intervention_id, rang);
