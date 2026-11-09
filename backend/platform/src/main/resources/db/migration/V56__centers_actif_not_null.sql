-- KB-ORG-09 (lot L7, ADR 0115) : un centre a toujours un état (actif ou non).
--
-- Effet comportemental UNIQUE : les centres dont `actif` est NULL deviennent actifs ; tous les contextes les lisaient déjà comme actifs
-- SAUF le Stock, dont le contrôle de nuit des péremptions les ignorait (variante « stricte », ADR 0108). Ils ENTRENT donc dans ce contrôle.
-- A EXÉCUTER AVANT en production : le SELECT de contrôle de `.ddd/migration/verifications-production.md` §72.1 (nombre de centres sans état).
-- Si le résultat est 0, la migration n'ajoute que la contrainte et n'a aucun effet de comportement.
--
-- Additive et sans risque pour le démarrage : l'UPDATE précède le NOT NULL, aucune ligne ne peut faire échouer la contrainte.

UPDATE centers SET actif = TRUE WHERE actif IS NULL;
ALTER TABLE centers ALTER COLUMN actif SET DEFAULT TRUE;
ALTER TABLE centers ALTER COLUMN actif SET NOT NULL;
