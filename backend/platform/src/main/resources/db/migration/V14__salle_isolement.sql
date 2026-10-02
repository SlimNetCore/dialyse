-- Migration V14 : l'isolement devient une propriété de la salle (case « Salle d'isolement » à sa création).
--
-- Table sans entité JPA (voir aussi db/schema.sql). 'OUI' : salle réservée aux patients à risque infectieux. Les salles
-- listées jusqu'ici dans planning_parametres.salles_isolement sont reprises puis la liste est vidée.

ALTER TABLE salle
    ADD COLUMN IF NOT EXISTS isolement VARCHAR (3) NOT NULL DEFAULT 'NON';

UPDATE salle
SET isolement = 'OUI'
WHERE isolement = 'NON'
  AND EXISTS (SELECT 1
              FROM planning_parametres p
              WHERE p.center_id = salle.center_id
                AND p.salles_isolement LIKE '%' || CAST(salle.id AS VARCHAR(36)) || '%');

UPDATE planning_parametres
SET salles_isolement = NULL
WHERE salles_isolement IS NOT NULL;
