-- Migration V60 : archivage des quatre tables mortes (CLOT4, ADR 0207, accord écrit de l'utilisateur du 2026-11-16).
--
-- AUCUNE donnée n'est supprimée : chaque table est seulement RENOMMÉE en archive_<nom>. Index et contraintes restent tels
-- quels (leurs noms ne changent pas). Retour arrière : le RENAME inverse (ALTER TABLE archive_<nom> RENAME TO <nom>).
--
-- À NE JAMAIS JOUER EN PRODUCTION SANS SAUVEGARDE PRÉALABLE (voir verifications-production.md §89).
--
-- Aucun code vivant ne lit ni n'écrit ces tables (vérifié par recherche dans les sources de production).
-- IF EXISTS : idempotente, portable PostgreSQL 16 / H2.

ALTER TABLE IF EXISTS user_center_assignment RENAME TO archive_user_center_assignment;
ALTER TABLE IF EXISTS gmao_alertes_maintenance RENAME TO archive_gmao_alertes_maintenance;
ALTER TABLE IF EXISTS gmao_historique_maintenance RENAME TO archive_gmao_historique_maintenance;
ALTER TABLE IF EXISTS stock_parametres RENAME TO archive_stock_parametres;
