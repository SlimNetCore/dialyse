-- DDL H2 (tests et poste de développement) des tables dont la Planification est propriétaire (S-DDL3, ADR 0186).
-- Chargé par spring.sql.init.schema-locations (application.yml de la plateforme) ; en production le schéma vient de Flyway
-- (V10, V11, V16, V24, V25, V28, V47, V48). S-DDL3b (ADR 0187) : planning_parametres et le personnel soignant (infirmier, affectations,
-- absences, remplacements) sont ici ; la reprise des salles d'isolement lit planning_parametres ET écrit salle (Organisation) : ce
-- script se charge APRÈS organisation-schema.sql (la Planification dépend de l'Organisation, jamais l'inverse).

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
-- Reprise des calculs orphelins instance par instance (V48, revue P8 B1) : jeton d'instance et battement de coeur.
ALTER TABLE IF EXISTS planification_optimisation ADD COLUMN IF NOT EXISTS instance_id VARCHAR (64);
ALTER TABLE IF EXISTS planification_optimisation ADD COLUMN IF NOT EXISTS battement_le TIMESTAMP;

-- Planning calendaire figé d'une proposition (une ligne par exécution, semaine, salle et créneau) : détail JSON des
-- sept jours et textes prêts à imprimer (entete_i / cell_i, i = 0 dimanche … 6 samedi).
CREATE TABLE IF NOT EXISTS planification_calendrier_case
(
    run_id
    UUID
    NOT
    NULL,
    center_id
    UUID
    NOT
    NULL,
    semaine_debut
    DATE
    NOT
    NULL,
    salle_id
    UUID
    NOT
    NULL,
    salle_nom
    VARCHAR
(
    255
) NOT NULL,
    salle_ordre INTEGER NOT NULL,
    creneau_id UUID NOT NULL,
    creneau_libelle VARCHAR
(
    255
) NOT NULL,
    creneau_ordre INTEGER NOT NULL,
    jours TEXT NOT NULL,
    entete_0 VARCHAR
(
    40
), entete_1 VARCHAR
(
    40
), entete_2 VARCHAR
(
    40
), entete_3 VARCHAR
(
    40
),
    entete_4 VARCHAR
(
    40
), entete_5 VARCHAR
(
    40
), entete_6 VARCHAR
(
    40
),
    cell_0 TEXT, cell_1 TEXT, cell_2 TEXT, cell_3 TEXT, cell_4 TEXT, cell_5 TEXT, cell_6 TEXT,
    PRIMARY KEY
(
    run_id,
    semaine_debut,
    salle_id,
    creneau_id
)
    );
CREATE INDEX IF NOT EXISTS idx_planification_calendrier_centre ON planification_calendrier_case (center_id, run_id);

-- Améliorations de l'optimisation du planning : préférences des patients, profils des infirmiers, réglages du centre
-- et déplacements temporaires de séances (générateur en maintenance un jour donné).
CREATE TABLE IF NOT EXISTS planning_preference_patient
(
    center_id          UUID    NOT NULL,
    patient_id         UUID    NOT NULL,
    creneau_prefere_id UUID,
    seances_par_semaine INTEGER,
    jours_a_choisir    BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (center_id, patient_id)
);

CREATE TABLE IF NOT EXISTS infirmier_profil_planning
(
    center_id     UUID    NOT NULL,
    infirmier_id  UUID    NOT NULL,
    taux_activite INTEGER NOT NULL DEFAULT 100,
    competences   VARCHAR(100),
    PRIMARY KEY (center_id, infirmier_id)
);

CREATE TABLE IF NOT EXISTS planification_reglages
(
    center_id                UUID PRIMARY KEY,
    replanification_auto     BOOLEAN NOT NULL DEFAULT FALSE,
    heures_par_vacation      INTEGER NOT NULL DEFAULT 5,
    heures_hebdo_temps_plein INTEGER NOT NULL DEFAULT 40,
    repos_hebdo_min          INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS deplacement_temporaire
(
    id            UUID PRIMARY KEY,
    center_id     UUID      NOT NULL,
    patient_id    UUID      NOT NULL,
    date_seance   DATE      NOT NULL,
    salle_id      UUID      NOT NULL,
    creneau_id    UUID      NOT NULL,
    generateur_id UUID      NOT NULL,
    motif         VARCHAR(255),
    cree_le       TIMESTAMP NOT NULL,
    UNIQUE (center_id, patient_id, date_seance)
);
CREATE INDEX IF NOT EXISTS idx_deplacement_temporaire_date ON deplacement_temporaire (center_id, date_seance);

-- Paramétrage du planning d'un centre (contexte Planification, slice P1). Auparavant créée par Hibernate (entité retirée) ; en
-- production le schéma vient de Flyway (V10, V11, V16).
CREATE TABLE IF NOT EXISTS planning_parametres (
    center_id UUID NOT NULL PRIMARY KEY,
    jours_ouverts VARCHAR(100) NOT NULL,
    salles_isolement TEXT,
    patients_par_infirmier INTEGER,
    patients_par_poste_serie INTEGER,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Reprise des salles d'isolement de l'ancien paramétrage du planning (liste d'identifiants), une seule fois. La colonne
-- salle.isolement est créée par organisation-schema.sql (qui précède ce script).
UPDATE salle
SET isolement = 'OUI'
WHERE isolement = 'NON'
  AND EXISTS (SELECT 1
              FROM planning_parametres p
              WHERE p.center_id = salle.center_id
                AND p.salles_isolement LIKE '%' || CAST(salle.id AS VARCHAR(36)) || '%');
UPDATE planning_parametres
SET salles_isolement = NULL
WHERE salles_isolement IS NOT NULL;

-- ═══ Personnel soignant : infirmiers, roulement, absences et remplacements ═══

CREATE TABLE IF NOT EXISTS infirmier
(
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    matricule VARCHAR(50) NOT NULL,
    nom VARCHAR(255) NOT NULL,
    prenom VARCHAR(255),
    telephone VARCHAR(50),
    qualification VARCHAR(20) NOT NULL,
    habilite_isolement BOOLEAN NOT NULL DEFAULT FALSE,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (center_id, matricule)
);

CREATE TABLE IF NOT EXISTS infirmier_affectation
(
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    infirmier_id UUID NOT NULL,
    salle_id UUID NOT NULL,
    creneau_id UUID NOT NULL,
    jours VARCHAR(100) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_infirmier_affectation_infirmier ON infirmier_affectation (center_id, infirmier_id);

CREATE TABLE IF NOT EXISTS infirmier_absence
(
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    infirmier_id UUID NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    type VARCHAR(20) NOT NULL,
    motif VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_infirmier_absence_periode ON infirmier_absence (center_id, date_debut, date_fin);
-- Compte qui a saisi l'absence (V47) : nul pour les absences antérieures
ALTER TABLE IF EXISTS infirmier_absence ADD COLUMN IF NOT EXISTS declaree_par UUID;

CREATE TABLE IF NOT EXISTS infirmier_remplacement
(
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    date_jour DATE NOT NULL,
    salle_id UUID NOT NULL,
    creneau_id UUID NOT NULL,
    infirmier_id UUID NOT NULL,
    remplace_infirmier_id UUID,
    UNIQUE (center_id, date_jour, creneau_id, infirmier_id)
);

-- Lien facultatif d'un infirmier avec un compte utilisateur (un compte ne sert qu'une fiche par centre)
ALTER TABLE IF EXISTS infirmier ADD COLUMN IF NOT EXISTS user_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS ux_infirmier_user ON infirmier (center_id, user_id);
