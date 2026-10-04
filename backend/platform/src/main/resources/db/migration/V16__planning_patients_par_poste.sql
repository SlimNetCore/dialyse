-- Migration V16 : nombre de patients par poste et par série, paramétrable par centre (capacité théorique).
--
-- Hibernate (ddl-auto: update) ajoute la colonne ; ce script documente le schéma attendu. NULL = valeur par défaut (3).

ALTER TABLE planning_parametres
    ADD COLUMN IF NOT EXISTS patients_par_poste_serie integer;
