-- DDL H2 (tests et poste de développement) des tables dont le contexte Patient et couverture est propriétaire (S-DDL3b, ADR 0187).
-- Chargé par spring.sql.init.schema-locations (application.yml de la plateforme) ; en production le schéma vient de Flyway (V1).

-- Table des patients (S6c, ADR 0133) : l'entité JPA PatientJpaEntity qui la faisait créer par Hibernate est retirée ; mêmes colonnes,
-- mêmes largeurs (VARCHAR(255), TEXT = VARCHAR(1000000000) en H2), mêmes nullités, aucune valeur par défaut, comme le DDL généré.
CREATE TABLE IF NOT EXISTS patients (
    id                      UUID PRIMARY KEY,
    adresse                 VARCHAR(255),
    assure_adresse          VARCHAR(255),
    assure_date_naissance   DATE,
    assure_groupe_sanguin   VARCHAR(255),
    assure_history_json     TEXT,
    assure_nom              VARCHAR(255),
    assure_numero_assurance VARCHAR(255),
    assure_prenom           VARCHAR(255),
    assure_sexe             VARCHAR(255),
    assure_tel_bureau       VARCHAR(255),
    assure_tel_mobile       VARCHAR(255),
    assure_tel_personnel    VARCHAR(255),
    categorie_transport_id  UUID,
    center_id               UUID         NOT NULL,
    centre_payeur_id        UUID,
    civilite                VARCHAR(255),
    code_patient            VARCHAR(255),
    created_at              TIMESTAMP(6) WITH TIME ZONE,
    date_admission          DATE         NOT NULL,
    date_evenement_etat     DATE,
    date_naissance          DATE,
    email                   VARCHAR(255),
    en_sommeil              BOOLEAN,
    epo_date                DATE,
    epo_enabled             BOOLEAN,
    etat_patient            VARCHAR(255),
    fer_date                DATE,
    fer_enabled             BOOLEAN,
    generateur_id           UUID,
    groupe_sanguin          VARCHAR(255),
    jour_dimanche           BOOLEAN,
    jour_jeudi              BOOLEAN,
    jour_lundi              BOOLEAN,
    jour_mardi              BOOLEAN,
    jour_mercredi           BOOLEAN,
    jour_samedi             BOOLEAN,
    jour_vendredi           BOOLEAN,
    lieu_naissance          VARCHAR(255),
    medecin_traitant_id     UUID,
    nom                     VARCHAR(255) NOT NULL,
    nombre_enfants          INTEGER,
    numero_assurance        VARCHAR(255) NOT NULL,
    observation             TEXT,
    photo_base64            TEXT,
    pieces_jointes_json     TEXT,
    position_id             UUID,
    prenom                  VARCHAR(255) NOT NULL,
    profession1             VARCHAR(255),
    qualite_assure          VARCHAR(255),
    salle_id                UUID,
    sexe                    VARCHAR(255) NOT NULL,
    situation_familiale     VARCHAR(255),
    sous_kt                 BOOLEAN,
    tel_bureau              VARCHAR(255),
    tel_mobile              VARCHAR(255),
    tel_personnel           VARCHAR(255),
    transporteur_aller_id   UUID,
    transporteur_retour_id  UUID,
    type_patient            VARCHAR(255) NOT NULL
);

-- PL1 (a) : les trois entités JPA des payeurs sont retirées ; ce DDL reproduit EXACTEMENT ce que Hibernate (ddl-auto: update) créait
-- (colonnes nullables et longueurs 255 : l'ancien DDL, jamais appliqué car Hibernate passait avant, disait 50 / 500 / NOT NULL).
CREATE TABLE IF NOT EXISTS caisse_assurance (
    id UUID NOT NULL PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(255),
    nom VARCHAR(255),
    type_caisse VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS agence (
    id UUID NOT NULL PRIMARY KEY,
    caisse_id UUID,
    center_id UUID NOT NULL,
    code VARCHAR(255),
    nom VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS centre_payeur (
    id UUID NOT NULL PRIMARY KEY,
    adresse VARCHAR(255),
    agence_id UUID,
    center_id UUID NOT NULL,
    code VARCHAR(255),
    nom VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS transporteur (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    nom VARCHAR(255) NOT NULL,
    telephone VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS categorie_transport (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    libelle VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS forfait (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    prix DECIMAL(10,2)
);

CREATE TABLE IF NOT EXISTS assure (
    numero_assurance VARCHAR(255) PRIMARY KEY,
    center_id UUID NOT NULL,
    nom VARCHAR(255),
    prenom VARCHAR(255),
    sexe VARCHAR(255),
    date_naissance DATE,
    tel_personnel VARCHAR(255),
    tel_mobile VARCHAR(255),
    tel_bureau VARCHAR(255),
    adresse VARCHAR(255),
    groupe_sanguin VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS assure_patient (
    id UUID NOT NULL DEFAULT RANDOM_UUID(),
    patient_id UUID NOT NULL,
    numero_assurance VARCHAR(100) NOT NULL,
    center_id UUID NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    date_debut_affectation DATE,
    date_fin_affectation   DATE,
    date_affectation TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
-- Index de recherche pour les affectations actives (la contrainte d'unicité est gérée applicativement)
CREATE INDEX IF NOT EXISTS idx_assure_patient_primary_active
    ON assure_patient (patient_id, center_id, is_primary, date_fin_affectation);

-- Prise en charge (PC3) : mêmes colonnes que Flyway V1 ; en H2 la table n'est plus créée par une entité JPA.
CREATE TABLE IF NOT EXISTS prise_en_charge (
    id UUID NOT NULL,
    center_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    date_debut_demande DATE,
    date_debut_effectif DATE,
    date_fin_demande DATE,
    date_fin_effectif DATE,
    forfait_demande_id UUID,
    forfait_effectif_id UUID,
    patient_id UUID NOT NULL,
    statut VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

-- Attestation de droits (PC4) : mêmes colonnes que Flyway V1 ; en H2 la table n'est plus créée par une entité JPA.
CREATE TABLE IF NOT EXISTS attestation_droit (
    id UUID NOT NULL,
    center_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    patient_id UUID NOT NULL,
    PRIMARY KEY (id)
);

-- Historique des mouvements de patients (append-only) : admission, séjour temporaire, sorties, libération de la place
CREATE TABLE IF NOT EXISTS mouvement_patient
(
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    date_effet DATE NOT NULL,
    etat_precedent VARCHAR(30),
    etat_nouveau VARCHAR(30),
    salle_id UUID,
    position_id UUID,
    generateur_id UUID,
    jours_dialyse VARCHAR(80),
    automatique BOOLEAN NOT NULL DEFAULT FALSE,
    cree_le TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_mouvement_patient_centre ON mouvement_patient (center_id, date_effet);
CREATE INDEX IF NOT EXISTS idx_mouvement_patient_patient ON mouvement_patient (center_id, patient_id);
