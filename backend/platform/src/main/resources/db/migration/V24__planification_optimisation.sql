-- Migration V24 : historique des optimisations du planning (moteur Timefold).
--
-- Cette table est aussi créée au démarrage par db/schema.sql (idempotent) ; ce script documente le schéma attendu
-- et permet de le préparer à la main sur PostgreSQL.

-- Optimisation du planning (Timefold) : historique des propositions d'un centre. parametres, resume et resultat sont
-- des documents JSON ; empreinte identifie l'état du centre lu au lancement (une proposition périmée n'est pas appliquée).
CREATE TABLE IF NOT EXISTS planification_optimisation
(
    id          UUID PRIMARY KEY,
    center_id   UUID        NOT NULL,
    statut      VARCHAR(20) NOT NULL,
    perimetre   VARCHAR(20) NOT NULL,
    parametres  TEXT        NOT NULL,
    cree_le     TIMESTAMP   NOT NULL,
    termine_le  TIMESTAMP,
    lance_par   VARCHAR(100),
    phase       VARCHAR(20),
    score       VARCHAR(255),
    empreinte   VARCHAR(64) NOT NULL,
    resume      TEXT,
    resultat    TEXT,
    erreur      TEXT,
    applique_le TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_planification_optimisation_centre ON planification_optimisation (center_id, cree_le);
