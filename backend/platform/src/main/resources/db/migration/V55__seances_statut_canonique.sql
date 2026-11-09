-- Migration V55 : statut de séance canonique (KB-SEA-82, 70, 73, 88, ADR 0114, lot L3).
--
-- ADDITIVE ET SANS RISQUE POUR LE DÉMARRAGE : une seule contrainte CHECK, ajoutée NOT VALID. PostgreSQL ne relit PAS les lignes existantes
-- à l'ajout : une donnée de production non canonique (statut en minuscules ou avec un blanc) NE PEUT PAS faire échouer Flyway ni empêcher
-- l'application de démarrer. Aucun DROP, aucun DELETE, aucune donnée modifiée.
--
-- EFFET : les NOUVELLES insertions et les mises à jour d'une ligne de seances doivent porter un statut canonique (MAJUSCULES, sans blanc
-- de bord). ATTENTION : PostgreSQL réévalue un CHECK NOT VALID sur toute ligne MISE À JOUR, quelle que soit la colonne modifiée : une
-- ligne déjà non canonique ne pourra plus être mise à jour (toute écriture sur cette ligne) tant qu'elle n'est pas corrigée ;
-- c'est le refus explicite voulu, la ligne reste lisible et le reste de l'application n'est pas touché. D'où le préalable ci-dessous.
--
-- VALIDATION (À FAIRE À LA MAIN, hors Flyway, après nettoyage) :
--   1. Détecter les statuts non canoniques (SELECT seule ; point 62.7 de .ddd/migration/verifications-production.md, et sa variante
--      pour les blancs, point 76.1) :
--        SELECT center_id, statut, count(*) FROM seances WHERE statut <> upper(trim(statut)) GROUP BY center_id, statut;
--      Chaque statut est à normaliser par décision écrite, statut par statut (un « facturee » normalisé en FACTUREE change les comptes du
--      tableau de bord et du Stock : KB-SEA-70, à valider par les métiers).
--   2. Quand la requête rend 0 ligne :
--        ALTER TABLE seances VALIDATE CONSTRAINT ck_seances_statut_canonique;
--
-- PostgreSQL seulement : la base H2 des tests est bâtie par db/schema.sql (sans Flyway) et ne reçoit pas cette contrainte (les matrices de
-- caractérisation y posent volontairement des statuts non canoniques pour décrire les lecteurs) ; le contrat PostgreSQL 16 l'exécute.
-- Les lecteurs ne sont PAS modifiés (KB-SEA-82) : ils restent en casse exacte, comme la Facturation (KB-FAC-17).

ALTER TABLE seances
    ADD CONSTRAINT ck_seances_statut_canonique CHECK (statut = UPPER(TRIM(statut))) NOT VALID;
