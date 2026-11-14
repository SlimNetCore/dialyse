-- DDL H2 (tests et poste de développement) des tables dont la Planification est propriétaire (S-DDL3, ADR 0186).
-- Chargé par spring.sql.init.schema-locations (application.yml de la plateforme) ; en production le schéma vient de Flyway
-- (V24, V25, V28, V48). Restent dans db/schema.sql : planning_parametres (le script de reprise des salles d'isolement l'écrit en même
-- temps que salle, propriété de l'Organisation) et ce qui s'y rattache.

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
