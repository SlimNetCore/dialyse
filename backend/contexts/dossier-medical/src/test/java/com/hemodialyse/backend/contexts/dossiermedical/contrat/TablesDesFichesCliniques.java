package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import org.springframework.jdbc.core.JdbcTemplate;

/** Schéma H2 des trois tables réelles de DM5 (colonnes et types du schéma V1 ; aucune contrainte d'unicité ni clé étrangère). */
final class TablesDesFichesCliniques {
    private TablesDesFichesCliniques() {
    }

    static void creer(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS antecedents_medicaux (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL, date_debut DATE NOT NULL, date_fin DATE, diagnostic_code VARCHAR(20), "
                + "diagnostic_code_display VARCHAR(255), diagnostic_code_system VARCHAR(10), libelle_libre VARCHAR(255), note TEXT, "
                + "patient_id UUID NOT NULL, severite VARCHAR(50), statut_clinique VARCHAR(20) NOT NULL, type_antecedent VARCHAR(20) NOT NULL, "
                + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS allergies_patient (id UUID PRIMARY KEY, categorie VARCHAR(20) NOT NULL, center_id UUID NOT NULL, "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL, criticite VARCHAR(10) NOT NULL, date_constatation DATE NOT NULL, "
                + "manifestations TEXT, patient_id UUID NOT NULL, statut_verification VARCHAR(15) NOT NULL, substance_code VARCHAR(20) NOT NULL, "
                + "substance_code_display VARCHAR(255), substance_code_system VARCHAR(10), type_reaction VARCHAR(15) NOT NULL, "
                + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS abords_vasculaires (id UUID PRIMARY KEY, actif BOOLEAN NOT NULL, center_id UUID NOT NULL, "
                + "complications VARCHAR(500), cote VARCHAR(10), created_at TIMESTAMP WITH TIME ZONE, date_creation DATE, date_fin DATE, "
                + "localisation VARCHAR(255), patient_id UUID NOT NULL, type_abord VARCHAR(20) NOT NULL)");
    }
}
