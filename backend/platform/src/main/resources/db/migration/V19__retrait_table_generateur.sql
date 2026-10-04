-- Migration V19 : retrait de l'ancien référentiel plat « generateur » (obsolète depuis le module GMAO v2).
--
-- Les générateurs de dialyse sont l'agrégat GMAO Equipement (type GENERATEUR_DIALYSE, table gmao_equipements), seule
-- source de vérité : plus aucun code ne lit ni n'écrit la table « generateur ». Les anciennes lignes avaient été
-- recopiées vers gmao_equipements avec le même identifiant (patients.generateur_id, sans clé étrangère, reste valide).
--
-- Garde-fou : la table n'est supprimée que si TOUTES ses lignes figurent déjà dans gmao_equipements ; sinon la
-- migration échoue explicitement plutôt que de perdre des données — recopier d'abord les lignes listées (même id, type
-- GENERATEUR_DIALYSE) puis relancer. Sans effet sur une base où la table n'existe plus.

DO
$$
DECLARE
manquantes INTEGER := 0;
BEGIN
    IF
to_regclass('public.generateur') IS NOT NULL THEN
        EXECUTE 'SELECT COUNT(*) FROM generateur g WHERE NOT EXISTS (SELECT 1 FROM gmao_equipements e WHERE e.id = g.id)'
            INTO manquantes;
        IF
manquantes > 0 THEN
            RAISE EXCEPTION 'Table generateur : % ligne(s) non recopiée(s) dans gmao_equipements — suppression refusée', manquantes;
END IF;
DROP TABLE public.generateur;
END IF;
END
$$;
