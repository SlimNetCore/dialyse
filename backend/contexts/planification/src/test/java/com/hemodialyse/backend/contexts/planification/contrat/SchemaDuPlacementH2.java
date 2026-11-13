package com.hemodialyse.backend.contexts.planification.contrat;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Les tables lues ou écrites par le placement, pour H2 (colonnes utiles seulement). Sur PostgreSQL 16, le schéma est celui de Flyway.
 */
final class SchemaDuPlacementH2 {

    private SchemaDuPlacementH2() {
    }

    static void creer(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS planning_parametres (center_id UUID NOT NULL PRIMARY KEY, "
                + "jours_ouverts VARCHAR(100) NOT NULL, salles_isolement TEXT, patients_par_infirmier INTEGER, "
                + "patients_par_poste_serie INTEGER, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS salle (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(50) NOT NULL, "
                + "nom VARCHAR(255) NOT NULL, isolement VARCHAR(3) NOT NULL DEFAULT 'NON', capacite INTEGER, actif BOOLEAN NOT NULL DEFAULT TRUE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS position_creneau (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "code VARCHAR(50) NOT NULL, libelle VARCHAR(255) NOT NULL, actif BOOLEAN NOT NULL DEFAULT TRUE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS center_holiday (id UUID PRIMARY KEY, center_id UUID NOT NULL, day_date DATE NOT NULL, "
                + "label VARCHAR(255))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS center_closure_day (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "day_date DATE NOT NULL, reason VARCHAR(255))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, center_id UUID NOT NULL, nom VARCHAR(255), "
                + "prenom VARCHAR(255), date_admission DATE, numero_assurance VARCHAR(255), sexe VARCHAR(10), type_patient VARCHAR(30), "
                + "etat_patient VARCHAR(30), date_evenement_etat DATE, salle_id UUID, position_id UUID, generateur_id UUID, "
                + "jour_dimanche BOOLEAN DEFAULT FALSE, jour_lundi BOOLEAN DEFAULT FALSE, jour_mardi BOOLEAN DEFAULT FALSE, "
                + "jour_mercredi BOOLEAN DEFAULT FALSE, jour_jeudi BOOLEAN DEFAULT FALSE, jour_vendredi BOOLEAN DEFAULT FALSE, "
                + "jour_samedi BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS serologies_patient (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "patient_id UUID NOT NULL, marqueur VARCHAR(15) NOT NULL, resultat VARCHAR(15) NOT NULL, "
                + "date_prelevement DATE NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
    }

    /** Vide ce que les tests du placement ont écrit pour ces centres. */
    static void vider(JdbcTemplate jdbc, Object... centres) {
        for (Object centre : centres) {
            for (String table : new String[]{"serologies_patient", "patients", "salle", "position_creneau", "center_holiday",
                    "center_closure_day", "planning_parametres"}) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
        }
    }
}
