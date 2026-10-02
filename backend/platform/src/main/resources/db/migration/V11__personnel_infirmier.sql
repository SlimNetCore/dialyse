-- Migration V11 : personnel soignant (infirmiers, roulement, absences, remplacements) et ratio de sécurité.
--
-- Ces tables sont aussi créées au démarrage par db/schema.sql (idempotent) ; ce script documente le schéma attendu
-- et permet de le préparer à la main sur PostgreSQL.

ALTER TABLE planning_parametres
    ADD COLUMN IF NOT EXISTS patients_par_infirmier integer;

CREATE TABLE IF NOT EXISTS infirmier
(
    id
    uuid
    PRIMARY
    KEY,
    center_id
    uuid
    NOT
    NULL,
    matricule
    varchar
(
    50
) NOT NULL,
    nom varchar
(
    255
) NOT NULL,
    prenom varchar
(
    255
),
    telephone varchar
(
    50
),
    qualification varchar
(
    20
) NOT NULL,
    habilite_isolement boolean NOT NULL DEFAULT FALSE,
    actif boolean NOT NULL DEFAULT TRUE,
    UNIQUE
(
    center_id,
    matricule
)
    );

CREATE TABLE IF NOT EXISTS infirmier_affectation
(
    id
    uuid
    PRIMARY
    KEY,
    center_id
    uuid
    NOT
    NULL,
    infirmier_id
    uuid
    NOT
    NULL,
    salle_id
    uuid
    NOT
    NULL,
    creneau_id
    uuid
    NOT
    NULL,
    jours
    varchar
(
    100
) NOT NULL
    );
CREATE INDEX IF NOT EXISTS idx_infirmier_affectation_infirmier ON infirmier_affectation (center_id, infirmier_id);

CREATE TABLE IF NOT EXISTS infirmier_absence
(
    id
    uuid
    PRIMARY
    KEY,
    center_id
    uuid
    NOT
    NULL,
    infirmier_id
    uuid
    NOT
    NULL,
    date_debut
    date
    NOT
    NULL,
    date_fin
    date
    NOT
    NULL,
    type
    varchar
(
    20
) NOT NULL,
    motif varchar
(
    255
)
    );
CREATE INDEX IF NOT EXISTS idx_infirmier_absence_periode ON infirmier_absence (center_id, date_debut, date_fin);

CREATE TABLE IF NOT EXISTS infirmier_remplacement
(
    id
    uuid
    PRIMARY
    KEY,
    center_id
    uuid
    NOT
    NULL,
    date_jour
    date
    NOT
    NULL,
    salle_id
    uuid
    NOT
    NULL,
    creneau_id
    uuid
    NOT
    NULL,
    infirmier_id
    uuid
    NOT
    NULL,
    remplace_infirmier_id
    uuid,
    UNIQUE
(
    center_id,
    date_jour,
    creneau_id,
    infirmier_id
)
    );

-- Lien facultatif d'un infirmier avec un compte utilisateur (un compte ne sert qu'une fiche par centre)
ALTER TABLE infirmier
    ADD COLUMN IF NOT EXISTS user_id uuid;
CREATE UNIQUE INDEX IF NOT EXISTS ux_infirmier_user ON infirmier (center_id, user_id);
