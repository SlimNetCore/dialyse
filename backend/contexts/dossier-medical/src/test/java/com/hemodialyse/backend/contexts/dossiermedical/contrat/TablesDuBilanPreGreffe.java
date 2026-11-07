package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import org.springframework.jdbc.core.JdbcTemplate;

/** Schéma H2 des quatre tables réelles de DM7 (colonnes et types du schéma V1 ; aucune contrainte d'unicité ni clé étrangère). */
final class TablesDuBilanPreGreffe {
    private TablesDuBilanPreGreffe() {
    }

    static void creer(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS bilans_pre_greffe (id UUID PRIMARY KEY, center_id UUID NOT NULL, conclusion_nephrologue TEXT, "
                + "contre_indications TEXT, created_at TIMESTAMP WITH TIME ZONE NOT NULL, date_debut_bilan DATE, date_greffe DATE, "
                + "date_inscription_liste_attente DATE, groupe_sanguin_confirme VARCHAR(5), patient_id UUID NOT NULL, pra_classe_i NUMERIC(5, 2), "
                + "pra_classe_ii NUMERIC(5, 2), statut VARCHAR(30) NOT NULL, typage_hla VARCHAR(500), updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS decisions_rcp (id UUID PRIMARY KEY, avis VARCHAR(15) NOT NULL, bilan_id UUID NOT NULL, "
                + "compte_rendu TEXT, date_reunion DATE NOT NULL, prochaine_date_revue DATE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS etapes_bilan_pre_greffe (id UUID PRIMARY KEY, patient_id UUID NOT NULL, center_id UUID NOT NULL, "
                + "categorie VARCHAR(20) NOT NULL, libelle VARCHAR(255) NOT NULL, statut VARCHAR(20) NOT NULL, date_realisation DATE, "
                + "resultat TEXT, date_expiration DATE, demande_examen_id UUID, serologie_id UUID, created_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS donneurs_vivants (id UUID PRIMARY KEY, patient_id UUID NOT NULL, center_id UUID NOT NULL, "
                + "nom VARCHAR(100) NOT NULL, prenom VARCHAR(100), date_naissance DATE, lien_parente VARCHAR(20) NOT NULL, telephone VARCHAR(30), "
                + "groupe_sanguin VARCHAR(5), typage_hla VARCHAR(500), statut_bilan VARCHAR(20) NOT NULL, crossmatch_resultat VARCHAR(15) NOT NULL, "
                + "date_crossmatch DATE, bilan_realise TEXT, contre_indications TEXT, decision_finale TEXT, date_decision DATE, "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
    }

    static void vider(JdbcTemplate jdbc, Object... centres) {
        for (Object centre : centres) {
            jdbc.update("DELETE FROM decisions_rcp WHERE bilan_id IN (SELECT id FROM bilans_pre_greffe WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM bilans_pre_greffe WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM etapes_bilan_pre_greffe WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM donneurs_vivants WHERE center_id = ?", centre);
        }
    }
}
