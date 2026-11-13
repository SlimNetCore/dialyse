package com.hemodialyse.backend.contexts.planification.contrat;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Les tables lues ou écrites par le paramétrage, pour H2 (colonnes utiles seulement). Sur PostgreSQL 16, le schéma est celui de
 * Flyway.
 */
final class SchemaDuParametrageH2 {

    private static final String[] TABLES = {"planning_parametres", "planification_reglages", "planning_preference_patient",
            "infirmier_profil_planning", "infirmier", "patients", "position_creneau", "salle"};

    private SchemaDuParametrageH2() {
    }

    static void creer(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS planning_parametres (center_id UUID NOT NULL PRIMARY KEY, "
                + "jours_ouverts VARCHAR(100) NOT NULL, salles_isolement TEXT, patients_par_infirmier INTEGER, "
                + "patients_par_poste_serie INTEGER, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS planification_reglages (center_id UUID PRIMARY KEY, "
                + "replanification_auto BOOLEAN NOT NULL DEFAULT FALSE, heures_par_vacation INTEGER NOT NULL DEFAULT 5, "
                + "heures_hebdo_temps_plein INTEGER NOT NULL DEFAULT 40, repos_hebdo_min INTEGER NOT NULL DEFAULT 1)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS planning_preference_patient (center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                + "creneau_prefere_id UUID, seances_par_semaine INTEGER, jours_a_choisir BOOLEAN NOT NULL DEFAULT FALSE, "
                + "PRIMARY KEY (center_id, patient_id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS infirmier_profil_planning (center_id UUID NOT NULL, infirmier_id UUID NOT NULL, "
                + "taux_activite INTEGER NOT NULL DEFAULT 100, competences VARCHAR(100), PRIMARY KEY (center_id, infirmier_id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS salle (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(50) NOT NULL, "
                + "nom VARCHAR(255) NOT NULL, isolement VARCHAR(3) NOT NULL DEFAULT 'NON', capacite INTEGER, actif BOOLEAN NOT NULL DEFAULT TRUE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS position_creneau (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "code VARCHAR(50) NOT NULL, libelle VARCHAR(255) NOT NULL, actif BOOLEAN NOT NULL DEFAULT TRUE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS infirmier (id UUID PRIMARY KEY, center_id UUID NOT NULL, matricule VARCHAR(50) NOT NULL, "
                + "nom VARCHAR(255) NOT NULL, prenom VARCHAR(255), telephone VARCHAR(50), qualification VARCHAR(30) NOT NULL, "
                + "habilite_isolement BOOLEAN NOT NULL DEFAULT FALSE, actif BOOLEAN NOT NULL DEFAULT TRUE, user_id UUID, "
                + "UNIQUE (center_id, matricule))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, center_id UUID NOT NULL, code_patient VARCHAR(50), "
                + "sexe VARCHAR(10), date_admission DATE, numero_assurance VARCHAR(255), type_patient VARCHAR(30), "
                + "created_at TIMESTAMP WITH TIME ZONE, nom VARCHAR(255), "
                + "prenom VARCHAR(255), etat_patient VARCHAR(30), position_id UUID, jour_dimanche BOOLEAN DEFAULT FALSE, "
                + "jour_lundi BOOLEAN DEFAULT FALSE, jour_mardi BOOLEAN DEFAULT FALSE, jour_mercredi BOOLEAN DEFAULT FALSE, "
                + "jour_jeudi BOOLEAN DEFAULT FALSE, jour_vendredi BOOLEAN DEFAULT FALSE, jour_samedi BOOLEAN DEFAULT FALSE)");
    }

    /** Tables de la slice P2 : le centre, les comptes (Identité et accès) et le roulement (P3), colonnes utiles seulement. */
    static void creerLesInfirmiers(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS centers (id UUID PRIMARY KEY, code VARCHAR(50), name VARCHAR(255), "
                + "fuseau_horaire VARCHAR(50), actif BOOLEAN NOT NULL DEFAULT TRUE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS infirmier_absence (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "infirmier_id UUID NOT NULL, date_debut DATE NOT NULL, date_fin DATE NOT NULL, type VARCHAR(20) NOT NULL, "
                + "motif VARCHAR(255), declaree_par UUID)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS infirmier_remplacement (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "date_jour DATE NOT NULL, salle_id UUID NOT NULL, creneau_id UUID NOT NULL, infirmier_id UUID NOT NULL, "
                + "remplace_infirmier_id UUID, UNIQUE (center_id, date_jour, creneau_id, infirmier_id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS app_role (id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, "
                + "name VARCHAR(100) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS app_user (id UUID PRIMARY KEY, username VARCHAR(80) NOT NULL UNIQUE, "
                + "password_hash VARCHAR(255) NOT NULL, email VARCHAR(150), full_name VARCHAR(150), "
                + "active BOOLEAN NOT NULL DEFAULT TRUE, must_change_password BOOLEAN NOT NULL DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS app_user_role (user_id UUID NOT NULL, role_id UUID NOT NULL, "
                + "PRIMARY KEY (user_id, role_id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS app_user_center (user_id UUID NOT NULL, center_id UUID NOT NULL, "
                + "PRIMARY KEY (user_id, center_id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS infirmier_affectation (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "infirmier_id UUID NOT NULL, salle_id UUID NOT NULL, creneau_id UUID NOT NULL, jours VARCHAR(100) NOT NULL)");
    }

    /** Vide les comptes rattachés aux centres de test, le roulement, les fiches et les centres donnés. */
    static void viderLesInfirmiers(JdbcTemplate jdbc, Object... centres) {
        for (Object centre : centres) {
            for (java.util.UUID compte : jdbc.queryForList("SELECT user_id FROM app_user_center WHERE center_id = ?",
                    java.util.UUID.class, centre)) {
                jdbc.update("DELETE FROM app_user_role WHERE user_id = ?", compte);
                jdbc.update("DELETE FROM app_user_center WHERE user_id = ?", compte);
                jdbc.update("DELETE FROM app_user WHERE id = ?", compte);
            }
            jdbc.update("DELETE FROM infirmier_affectation WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM infirmier_absence WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM infirmier_remplacement WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM infirmier WHERE center_id = ?", centre);
        }
        for (Object centre : centres) {
            jdbc.update("DELETE FROM centers WHERE id = ?", centre);
        }
    }

    static void vider(JdbcTemplate jdbc, Object... centres) {
        for (Object centre : centres) {
            for (String table : TABLES) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
        }
    }
}
