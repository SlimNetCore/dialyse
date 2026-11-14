-- DDL H2 (tests et poste de développement) des tables dont l'Organisation est propriétaire (S-DDL3b, ADR 0187).
-- Chargé par spring.sql.init.schema-locations (application.yml de la plateforme) ; en production le schéma vient de Flyway.
-- ORDRE : ce script passe AVANT db/schema.sql (identité) : app_user_center y référence centers par clé étrangère.

-- S-DDL1 (2026-11-14) : societes et centers décrivent EXACTEMENT Flyway V1 (baseline) + V46 (fuseau_horaire) + V56 (centers.actif NOT NULL
-- DEFAULT TRUE) : mêmes colonnes, mêmes largeurs, mêmes nullités, mêmes unicités (société.code).
CREATE TABLE IF NOT EXISTS societes (
    id UUID NOT NULL,
    actif BOOLEAN NOT NULL,
    adresse VARCHAR(250),
    code VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    email VARCHAR(150),
    logo BYTEA,
    logo_content_type VARCHAR(30),
    nif VARCHAR(40),
    nis VARCHAR(40),
    pied_page VARCHAR(500),
    raison_sociale VARCHAR(200) NOT NULL,
    rc VARCHAR(40),
    site_web VARCHAR(200),
    telephone VARCHAR(30),
    ville VARCHAR(100),
    wilaya VARCHAR(100),
    PRIMARY KEY (id),
    CONSTRAINT uk_societes_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS centers (
    id UUID NOT NULL,
    actif BOOLEAN DEFAULT TRUE NOT NULL,
    adresse VARCHAR(250),
    code VARCHAR(255),
    email VARCHAR(150),
    fuseau_horaire VARCHAR(50),
    name VARCHAR(255),
    site_web VARCHAR(200),
    societe_id UUID NOT NULL,
    telephone VARCHAR(30),
    ville VARCHAR(100),
    wilaya VARCHAR(100),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS medecin (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    nom VARCHAR(255) NOT NULL,
    prenom VARCHAR(255),
    specialite VARCHAR(255),
    actif BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS machine
(
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(100) NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    actif BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS protocole
(
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(100) NOT NULL,
    libelle VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS salle (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    nom VARCHAR(255) NOT NULL,
    ouverte BOOLEAN NOT NULL DEFAULT TRUE,
    actif BOOLEAN NOT NULL DEFAULT TRUE
);

-- Salle d'isolement : propriété de la salle (créée avec elle). 'OUI' = réservée aux patients à risque infectieux.
-- (La reprise de l'ancien paramétrage du planning, UPDATE salle ... planning_parametres, est dans planification-schema.sql.)
ALTER TABLE IF EXISTS salle ADD COLUMN IF NOT EXISTS isolement VARCHAR (3) NOT NULL DEFAULT 'NON';

-- Capacité d une salle : nombre maximal de générateurs affectés (NULL = illimitée)
ALTER TABLE IF EXISTS salle ADD COLUMN IF NOT EXISTS capacite INTEGER;

-- Les générateurs de dialyse sont l'agrégat GMAO Equipement (type GENERATEUR_DIALYSE, table gmao_equipements, gérée
-- par Hibernate ddl-auto). L'ancienne table plate "generateur" n'existe plus (supprimée par la migration V19).

CREATE TABLE IF NOT EXISTS position_creneau (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    actif BOOLEAN NOT NULL DEFAULT TRUE
);

-- ═══ Calendrier centre (jours fériés / fermetures) ═══

CREATE TABLE IF NOT EXISTS center_holiday
(
    id        UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    day_date  DATE NOT NULL,
    label     VARCHAR(255),
    UNIQUE (center_id, day_date)
);

CREATE TABLE IF NOT EXISTS center_closure_day
(
    id        UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    day_date  DATE NOT NULL,
    reason    VARCHAR(255),
    UNIQUE (center_id, day_date)
);
