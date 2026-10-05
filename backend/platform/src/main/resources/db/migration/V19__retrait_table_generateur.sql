-- Migration V19 : retrait de l'ancien référentiel plat « generateur » (obsolète depuis le module GMAO v2).
--
-- Les générateurs de dialyse sont l'agrégat GMAO Equipement (type GENERATEUR_DIALYSE, table gmao_equipements), seule
-- source de vérité : plus aucun code ne lit ni n'écrit la table « generateur ». Les anciennes valeurs ne sont pas
-- conservées : la suppression est inconditionnelle et ne dépend d'aucune recopie vers gmao_equipements.
-- (patients.generateur_id n'a pas de clé étrangère : il n'est pas modifié.)
-- Sans effet sur une base où la table n'existe plus.

DROP TABLE IF EXISTS generateur;
