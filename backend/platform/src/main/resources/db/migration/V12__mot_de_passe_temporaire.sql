-- Migration V12 : mot de passe temporaire à remplacer à la première connexion.
--
-- Hibernate (ddl-auto: update) ajoute la colonne au démarrage ; ce script documente le schéma attendu. Un compte
-- créé depuis une fiche infirmier reçoit un mot de passe temporaire : tant que l'indicateur est vrai, l'API répond 403
-- PASSWORD_CHANGE_REQUIRED hors routes d'authentification.

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS must_change_password boolean NOT NULL DEFAULT false;
