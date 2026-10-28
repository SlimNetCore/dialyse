package com.hemodialyse.backend.contexts.planification.contrat;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Les tables de l'optimisation (P8), pour H2 : celles du schéma Flyway. Sur PostgreSQL 16, le schéma est celui de Flyway.
 */
final class SchemaDeLOptimisationH2 {

    private SchemaDeLOptimisationH2() {
    }

    static void creer(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS planification_optimisation (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "statut VARCHAR(20) NOT NULL, perimetre VARCHAR(20) NOT NULL, parametres TEXT NOT NULL, cree_le TIMESTAMP NOT NULL, "
                + "termine_le TIMESTAMP, lance_par VARCHAR(100), phase VARCHAR(20), score VARCHAR(255), empreinte VARCHAR(64) NOT NULL, "
                + "resume TEXT, resultat TEXT, erreur TEXT, applique_le TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS planification_calendrier_case (run_id UUID NOT NULL, center_id UUID NOT NULL, "
                + "semaine_debut DATE NOT NULL, salle_id UUID NOT NULL, salle_nom VARCHAR(255) NOT NULL, salle_ordre INTEGER NOT NULL, "
                + "creneau_id UUID NOT NULL, creneau_libelle VARCHAR(255) NOT NULL, creneau_ordre INTEGER NOT NULL, jours TEXT NOT NULL, "
                + "entete_0 VARCHAR(40), entete_1 VARCHAR(40), entete_2 VARCHAR(40), entete_3 VARCHAR(40), entete_4 VARCHAR(40), "
                + "entete_5 VARCHAR(40), entete_6 VARCHAR(40), cell_0 TEXT, cell_1 TEXT, cell_2 TEXT, cell_3 TEXT, cell_4 TEXT, "
                + "cell_5 TEXT, cell_6 TEXT, PRIMARY KEY (run_id, semaine_debut, salle_id, creneau_id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS deplacement_temporaire (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "patient_id UUID NOT NULL, date_seance DATE NOT NULL, salle_id UUID NOT NULL, creneau_id UUID NOT NULL, "
                + "generateur_id UUID NOT NULL, motif VARCHAR(255), cree_le TIMESTAMP NOT NULL, "
                + "UNIQUE (center_id, patient_id, date_seance))");
    }

    /**
     * Vide ce que les tests de l'optimisation ont écrit pour ces centres.
     */
    static void vider(JdbcTemplate jdbc, Object... centres) {
        for (Object centre : centres) {
            for (String table : new String[]{"planification_calendrier_case", "deplacement_temporaire", "planification_optimisation"}) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
        }
    }
}
