-- Nettoyage de la BASE DE DEV LOCALE après l'abandon du Strangler progressif (ADR 0024).
-- À exécuter PAR VOUS, sur votre base locale uniquement (jamais sur une base partagée ni en production).
-- Contexte : les anciennes migrations V34, V36 (ancienne), V37 et V38 ont été supprimées du dépôt.
-- Si votre base locale les a déjà appliquées, Flyway refuse de démarrer tant que leurs lignes existent
-- dans flyway_schema_history. V35 (registre d'événements) et la V36 ACTUELLE (tâches d'intervention,
-- description « gmao taches intervention ») sont conservées.
--
-- ÉTAPE A — LECTURE SEULE : à lancer d'abord, ne modifie rien.

-- A1. Migrations appliquées de 34 à 38 (la ligne 36 est l'ANCIENNE seulement si sa description
--     n'est PAS 'gmao taches intervention').
SELECT installed_rank, version, description, success, checksum
FROM flyway_schema_history
WHERE version ~ '^[0-9]+$' AND version::int BETWEEN 34 AND 38
ORDER BY installed_rank;

-- A2. Le schéma gmao existe-t-il, avec quelles tables et combien de lignes ?
SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_schema = 'gmao'
ORDER BY table_name;
-- Pour chaque table listée ci-dessus, comptez ses lignes avant suppression, par ex. :
-- SELECT COUNT(*) FROM gmao.plan_maintenance;
-- SELECT COUNT(*) FROM gmao.intervenant;

-- A3. Publications d'événements en attente (table du registre, V35).
SELECT COUNT(*) AS publications_non_terminees
FROM event_publication
WHERE completion_date IS NULL;

-- Si A1 ne renvoie aucune ligne 34, 37, 38 ni 36 « ancienne » ET si A2 ne renvoie rien :
-- votre base est déjà propre, il n'y a RIEN à faire à l'étape B.
--
-- ÉTAPE B — MODIFICATION (SUPPRESSION) : à exécuter seulement si l'étape A montre des traces.
-- Les tables public.gmao_plans_maintenance et public.gmao_intervenants ne sont PAS touchées.
-- Exécutez dans une transaction pour pouvoir annuler (ROLLBACK) en cas de doute.

-- BEGIN;
--
-- DELETE FROM flyway_schema_history WHERE version IN ('34', '37', '38');
-- DELETE FROM flyway_schema_history
--  WHERE version = '36' AND description <> 'gmao taches intervention';  -- ne supprime rien si la 36 est l'actuelle
--
-- DROP SCHEMA IF EXISTS gmao CASCADE;   -- supprime les tables de l'ancienne approche (données de dev seulement)
--
-- -- Facultatif : purger les événements de dev non terminés du registre (V35 reste appliquée).
-- -- DELETE FROM event_publication WHERE completion_date IS NULL;
--
-- COMMIT;   -- ou ROLLBACK;
--
-- Après l'étape B, redémarrer l'application : Flyway doit valider sans erreur et appliquer, si besoin,
-- la V36 actuelle (ALTER TABLE gmao_taches_intervention, additif et rejouable).
