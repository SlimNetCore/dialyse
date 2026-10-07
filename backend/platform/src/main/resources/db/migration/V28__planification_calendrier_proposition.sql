-- Migration V28 : planning calendaire figé des propositions d'optimisation (impression et affichage).
--
-- Cette table est aussi créée au démarrage par db/schema.sql (idempotent) ; ce script documente le schéma attendu
-- et permet de le préparer à la main sur PostgreSQL.

-- Une ligne par exécution, semaine, salle et créneau. « jours » est le détail structuré des sept jours (JSON) ;
-- entete_i et cell_i (i = 0 dimanche … 6 samedi) sont les textes prêts à imprimer, lus par le modèle de document
-- « Planning proposé » (une seule requête SQL, sans calcul).
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
