-- Migration V23 : séance confirmée alors que le patient n'était pas programmé ce jour-là.
--
-- hors_planning est vrai quand l'infirmier (ou l'administrateur, pour un jour de fermeture) a confirmé le scan d'un
-- patient hors de ses jours de dialyse ; motif_hors_planning vaut RATTRAPAGE, URGENCE ou AUTRE (précision libre).
-- Hibernate (ddl-auto: update) ajoute ces colonnes au démarrage ; ce script documente le schéma (idempotent).

ALTER TABLE seances
    ADD COLUMN IF NOT EXISTS hors_planning BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE seances
    ADD COLUMN IF NOT EXISTS motif_hors_planning VARCHAR (20);
ALTER TABLE seances
    ADD COLUMN IF NOT EXISTS precision_hors_planning VARCHAR (255);
