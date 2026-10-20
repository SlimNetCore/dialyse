package com.hemodialyse.backend.characterization.facturation.validation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation F3 — refus de protocole, période invalide, autorisations par profil, isolement de centre,
 * transaction (aucune facture si échec) de {@code POST /facturation/validate}.
 */
class RefusEtAutorisationsCharacterizationIntegrationTest extends ValidationCharacterizationSupport {

    @Override
    protected String theme() {
        return "refus";
    }

    private void uneSeance() {
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");
    }

    private void rienNEstEmis() {
        assertEquals(0, compter("factures", CENTRE_A));
        assertEquals(0, compter("facture_lignes", CENTRE_A));
        assertEquals(0, compter("ecritures_comptables", CENTRE_A));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE center_id = ? AND statut = 'VALIDEE'",
                Integer.class, CENTRE_A));
        assertEquals(41, jdbc.queryForObject("SELECT seq_value FROM facture_sequence WHERE center_id = ? AND seq_year = ?",
                Integer.class, CENTRE_A, anneeCourante()));
    }

    // ------------------------------------------------------------------ période et corps

    @Test
    void sans_mois_ni_dates_la_validation_est_refusee_et_rien_n_est_emis() throws Exception {
        uneSeance();

        golden("sans_periode", valider(json("{'centerId':'" + CENTRE_A + "','userId':'u1','regroupementMultiForfait':true}")));
        rienNEstEmis();
        assertEquals(0, evenements.recus().size());
    }

    @Test
    void une_seule_date_ne_suffit_pas() throws Exception {
        uneSeance();

        golden("une_seule_date", valider(json("{'centerId':'" + CENTRE_A + "','userId':'u1','periodStart':'2026-01-01',"
                + "'regroupementMultiForfait':true}")));
        rienNEstEmis();
    }

    @Test
    void une_fin_avant_le_debut_est_refusee() throws Exception {
        uneSeance();

        golden("fin_avant_debut", valider(dates(CENTRE_A, "2026-01-31", "2026-01-01", true)));
        rienNEstEmis();
    }

    @Test
    void un_mois_illisible_est_refuse_en_erreur_interne() throws Exception {
        // KB-FAC-19 (suite) : comme l'aperçu, le mois illisible n'est pas traduit en 400.
        uneSeance();

        golden("mois_illisible", valider(mois(CENTRE_A, "2026-13", true)));
        golden("mois_texte", valider(mois(CENTRE_A, "janvier", true)));
        rienNEstEmis();
    }

    @Test
    void un_mois_vide_est_traite_comme_absent() throws Exception {
        uneSeance();

        golden("mois_vide_avec_dates", valider(json("{'centerId':'" + CENTRE_A + "','userId':'u1','month':'  ',"
                + "'periodStart':'2026-01-01','periodEnd':'2026-01-31','regroupementMultiForfait':true}")));
        goldenEtat("mois_vide_avec_dates_etat", CENTRE_A);
    }

    @Test
    void les_champs_obligatoires_du_corps_sont_controles() throws Exception {
        uneSeance();

        golden("sans_centre", valider(json("{'userId':'u1','month':'2026-01','regroupementMultiForfait':true}")));
        golden("sans_utilisateur", valider(json("{'centerId':'" + CENTRE_A + "','month':'2026-01','regroupementMultiForfait':true}")));
        golden("utilisateur_vide", valider(json("{'centerId':'" + CENTRE_A + "','userId':' ','month':'2026-01',"
                + "'regroupementMultiForfait':true}")));
        golden("centre_illisible", valider(json("{'centerId':'pas-un-uuid','userId':'u1','month':'2026-01',"
                + "'regroupementMultiForfait':true}")));
        golden("date_illisible", valider(json("{'centerId':'" + CENTRE_A + "','userId':'u1','periodStart':'31/01/2026',"
                + "'periodEnd':'2026-01-31','regroupementMultiForfait':true}")));
        golden("corps_illisible", valider("{pas du json"));
        golden("corps_vide", valider(""));
        rienNEstEmis();
    }

    @Test
    void un_regroupement_absent_du_corps_est_refuse_en_erreur_interne() throws Exception {
        // KB-FAC-19 (suite) : booléen primitif du DTO, comme pour l'aperçu.
        uneSeance();

        golden("regroupement_absent_500", valider(json("{'centerId':'" + CENTRE_A + "','userId':'u1','month':'2026-01'}")));
        rienNEstEmis();
    }

    @Test
    void l_horodatage_de_l_apercu_n_est_jamais_controle() throws Exception {
        // KB-FAC-30 : la validation ne vérifie pas que l'aperçu présenté à l'utilisateur est encore celui des séances.
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");

        golden("horodatage_apercu_ignore_200", valider(json("{'centerId':'" + CENTRE_A + "','userId':'u1','month':'2026-01',"
                + "'regroupementMultiForfait':true,'previewGeneratedAt':'n importe quoi, jamais controle'}")));
        goldenEtat("horodatage_apercu_ignore_etat", CENTRE_A);
    }

    @Test
    void les_reglages_du_centre_ne_choisissent_pas_le_regroupement_c_est_le_corps_qui_decide() throws Exception {
        jdbc.update("UPDATE facturation_settings SET regroupement_multi_forfait = FALSE WHERE center_id = ?", CENTRE_A);
        UUID surchargee = seance("AMRANI_26_PREMIUM", P_AMRANI, CENTRE_A, "2026-01-26", "VALIDEE");
        surcharge(surchargee, FORFAIT_PREMIUM, "Forfait HD Premium", "5200.00");
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");

        golden("reglage_ignore_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("reglage_ignore_etat", CENTRE_A);
    }

    // ------------------------------------------------------------------ autorisations et centre

    @Test
    void seul_l_administrateur_valide_la_facturation() throws Exception {
        uneSeance();
        String corps = mois(CENTRE_A, JANVIER, true);

        golden("autorisation_medecin", doPost("/validate", corps, as(CENTRE_A, "MEDECIN")));
        golden("autorisation_secretaire", doPost("/validate", corps, as(CENTRE_A, "SECRETAIRE")));
        golden("autorisation_infirmier", doPost("/validate", corps, as(CENTRE_A, "INFIRMIER")));
        golden("autorisation_anonyme", doPostAnonyme("/validate", corps));
        rienNEstEmis();
        golden("autorisation_admin", doPost("/validate", corps, adminA()));
    }

    @Test
    void le_medecin_administrateur_est_refuse_comme_le_medecin_seul() throws Exception {
        uneSeance();

        golden("autorisation_medecin_et_admin", doPost("/validate", mois(CENTRE_A, JANVIER, true),
                as(CENTRE_A, "MEDECIN", "ADMIN")));
    }

    @Test
    void un_administrateur_d_un_autre_centre_emet_les_factures_du_centre_designe_dans_le_corps() throws Exception {
        // Nouveau code F3 (KB-FAC-33 corrigé) : 403 si le centre du corps diffère de celui de la session ; rien n'est émis.
        // KB-FAC-14 (suite) : la validation ne compare pas le centre du corps à celui de la session.
        uneSeance();

        golden("centre_A_par_admin_B_nouveau_code", doPost("/validate", mois(CENTRE_A, JANVIER, true), as(CENTRE_B, "ADMIN")));
        goldenEtat("centre_A_par_admin_B_etat_nouveau_code", CENTRE_A);
        goldenEcritures("centre_A_par_admin_B_ecritures_nouveau_code", CENTRE_A);
    }

    @Test
    void un_centre_inconnu_donne_une_validation_vide_et_publie_quand_meme_l_evenement() throws Exception {
        // Nouveau code F3 (KB-FAC-33 corrigé) : un centre qui n'est pas celui de la session répond 403 ; aucun événement.
        UUID inconnu = UUID.fromString("99998000-0000-0000-0000-0000000000ff");

        golden("centre_inconnu_200", doPost("/validate", mois(inconnu, JANVIER, true), as(inconnu, "ADMIN")));
        goldenEvenements("centre_inconnu_evenements_nouveau_code");
    }

    // ------------------------------------------------------------------ transaction

    @Test
    void un_prix_negatif_n_existe_pas_la_ligne_est_refusee_et_toute_la_validation_est_annulee() throws Exception {
        // KB-FAC-31 : pas d'avoir ni de montant négatif dans la validation ; une surcharge négative (aucun contrôle à la
        // saisie de la séance) fait refuser la facture (400) alors que l'aperçu l'accepte.
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");
        UUID negative = seance("BELKACEM_06", P_BELKACEM, CENTRE_A, "2026-01-06", "VALIDEE");
        surcharge(negative, FORFAIT_HD, "Forfait HD (remise)", "-100.00");

        golden("prix_negatif_400", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("prix_negatif_etat", CENTRE_A);
        assertEquals(0, evenements.recus().size());
    }

    @Test
    void l_echec_d_une_facture_annule_toutes_les_autres_les_seances_et_la_sequence() throws Exception {
        // Nouveau code F3 (KB-FAC-24/25) : 409 explicite au lieu du 500.
        // KB-FAC-24 (suite) : deux patients sur trois sont émissibles, le troisième heurte un numéro existant.
        factureExistante(CENTRE_A, P_CHIKHI, "FAC-" + anneeCourante() + "-0044", "2025-12-01", "2025-12-31");
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");
        seance("BELKACEM_06", P_BELKACEM, CENTRE_A, "2026-01-06", "VALIDEE");
        seance("CHIKHI_07", P_CHIKHI, CENTRE_A, "2026-01-07", "VALIDEE");

        golden("echec_derniere_facture_500_nouveau_code", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("echec_derniere_facture_etat", CENTRE_A);
        assertEquals(0, evenements.recus().size());
        assertEquals(0, compter("ecritures_comptables", CENTRE_A));
    }
}
