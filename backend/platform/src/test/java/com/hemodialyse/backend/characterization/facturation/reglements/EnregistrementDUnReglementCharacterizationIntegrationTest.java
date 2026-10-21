package com.hemodialyse.backend.characterization.facturation.reglements;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * F4 : {@code POST /api/v1/reglements/{factureId}/paiements}. Enregistrement nominal, valeurs par défaut, arrondis,
 * refus (montant nul, facture inconnue, autre centre, corps illisibles) et doublons.
 */
class EnregistrementDUnReglementCharacterizationIntegrationTest extends ReglementsCharacterizationSupport {

    @Override
    protected String theme() {
        return "enregistrement";
    }

    @Test
    void un_reglement_partiel_est_enregistre_et_rend_la_situation_de_la_facture() throws Exception {
        UUID facture = factureMille();

        golden("partiel_200", enregistrer(facture, corps(CENTRE_A, "400.00", "2026-02-10", "caissier-1")));

        // Nouveau code F4 : auteur = utilisateur de la session.
        goldenReglements("partiel_base_nouveau_code", CENTRE_A);
    }

    @Test
    void un_reglement_du_montant_exact_solde_la_facture() throws Exception {
        UUID facture = factureMille();

        golden("solde_200", enregistrer(facture, corps(CENTRE_A, "1000.00", "2026-02-10", "caissier-1")));
    }

    @Test
    void sans_date_le_reglement_prend_le_jour_d_execution_et_sans_utilisateur_l_auteur_est_system() throws Exception {
        UUID facture = factureMille();

        golden("sans_date_ni_auteur_200", enregistrer(facture, corps(CENTRE_A, "100.00", null, null)));

        // Nouveau code F4 : auteur = utilisateur de la session (le userId du corps est ignoré).
        goldenReglements("sans_date_ni_auteur_base_nouveau_code", CENTRE_A);
    }

    @Test
    void un_utilisateur_vide_ou_avec_espaces_donne_system_ou_la_valeur_nettoyee() throws Exception {
        UUID facture = factureMille();

        enregistrer(facture, corps(CENTRE_A, "10.00", "2026-02-01", "   "));
        enregistrer(facture, corps(CENTRE_A, "20.00", "2026-02-02", "  caissier-2  "));
        enregistrer(facture, corps(CENTRE_A, "30.00", "2026-02-03", ""));

        // Nouveau code F4 : auteur = utilisateur de la session (le userId du corps est ignoré, nettoyé ou non).
        goldenReglements("auteur_nettoye_base_nouveau_code", CENTRE_A);
    }

    @Test
    void le_montant_est_arrondi_a_deux_decimales_half_up_et_le_total_regle_est_la_somme_des_montants_arrondis() throws Exception {
        UUID facture = factureMille();

        golden("arrondi_500_005_200", enregistrer(facture, corps(CENTRE_A, "500.005", "2026-02-01", "u")));
        golden("arrondi_100_004_200", enregistrer(facture, corps(CENTRE_A, "100.004", "2026-02-02", "u")));
        golden("arrondi_moins_0_005_200", enregistrer(facture, corps(CENTRE_A, "-0.005", "2026-02-03", "u")));

        // Nouveau code F4 : auteur = utilisateur de la session.
        goldenReglements("arrondi_base_nouveau_code", CENTRE_A);
    }

    @Test
    void un_montant_qui_s_arrondit_a_zero_est_refuse_apres_le_controle_de_non_nullite() throws Exception {
        UUID facture = factureMille();

        golden("montant_0_004_refuse", enregistrer(facture, corps(CENTRE_A, "0.004", "2026-02-01", "u")));

        assertEquals(0, compter("facture_reglements", CENTRE_A));
    }

    @Test
    void un_montant_nul_est_refuse_et_rien_n_est_enregistre() throws Exception {
        UUID facture = factureMille();

        golden("montant_zero_refuse", enregistrer(facture, corps(CENTRE_A, "0", "2026-02-01", "u")));
        golden("montant_zero_decimal_refuse", enregistrer(facture, corps(CENTRE_A, "0.00", "2026-02-01", "u")));

        assertEquals(0, compter("facture_reglements", CENTRE_A));
        assertEquals(0, evenements.recus().size());
    }

    @Test
    void un_montant_negatif_est_accepte_comme_remboursement() throws Exception {
        UUID facture = factureMille();
        enregistrer(facture, corps(CENTRE_A, "600.00", "2026-02-01", "u"));

        golden("remboursement_200", enregistrer(facture, corps(CENTRE_A, "-150.00", "2026-02-05", "u")));

        // Nouveau code F4 : auteur = utilisateur de la session.
        goldenReglements("remboursement_base_nouveau_code", CENTRE_A);
    }

    @Test
    void une_facture_inconnue_est_refusee_sans_rien_enregistrer() throws Exception {
        UUID inconnue = UUID.fromString("99997000-0000-0000-0000-00000000dead");

        golden("facture_inconnue", enregistrer(inconnue, corps(CENTRE_A, "100.00", "2026-02-01", "u")));

        assertEquals(0, compter("facture_reglements", CENTRE_A));
        assertEquals(0, evenements.recus().size());
    }

    @Test
    void une_facture_d_un_autre_centre_est_introuvable_pour_le_centre_du_corps() throws Exception {
        UUID factureB = facture(CENTRE_B, P_CENTRE_B, "FAC-B-0001", "2026-01-31", "500.00", PAYEUR_B, AGENCE_B);

        golden("facture_autre_centre_introuvable", enregistrer(factureB, corps(CENTRE_A, "100.00", "2026-02-01", "u")));

        assertEquals(0, compter("facture_reglements", CENTRE_A));
        assertEquals(0, compter("facture_reglements", CENTRE_B));
    }

    @Test
    void le_centre_du_corps_n_est_pas_controle_contre_celui_de_la_session() throws Exception {
        // KB-FAC-37 : un administrateur du centre A enregistre un règlement sur la facture du centre B en annonçant B.
        UUID factureB = facture(CENTRE_B, P_CENTRE_B, "FAC-B-0001", "2026-01-31", "500.00", PAYEUR_B, AGENCE_B);

        // Nouveau code F4 (KB-FAC-37 corrigé) : 403, le centre du corps n'est pas celui de la session.
        golden("centre_du_corps_autre_que_la_session_200_nouveau_code", enregistrer(factureB, corps(CENTRE_B, "100.00", "2026-02-01", "u")));

        // Nouveau code F4 : rien n'a été écrit au centre B (le legacy y avait enregistré le règlement : 1).
        assertEquals(0, compter("facture_reglements", CENTRE_B));
    }

    @Test
    void la_date_de_reglement_n_est_pas_controlee_contre_la_facture_ni_contre_le_jour() throws Exception {
        // KB-FAC-38 : une date antérieure à la facture, ou lointaine dans le futur, est acceptée.
        UUID facture = factureMille();

        golden("date_avant_la_facture_200", enregistrer(facture, corps(CENTRE_A, "10.00", "1999-01-01", "u")));
        golden("date_future_200", enregistrer(facture, corps(CENTRE_A, "10.00", "2999-12-31", "u")));

        // Nouveau code F4 : auteur = utilisateur de la session.
        goldenReglements("dates_non_controlees_base_nouveau_code", CENTRE_A);
    }

    @Test
    void un_double_envoi_identique_enregistre_deux_reglements() throws Exception {
        // KB-FAC-39 : aucune idempotence, aucune clé de dédoublonnage ; deux règlements, deux événements, deux codes.
        UUID facture = factureMille();
        String corps = corps(CENTRE_A, "250.00", "2026-02-10", "caissier");

        enregistrer(facture, corps);
        golden("double_envoi_second_200", enregistrer(facture, corps));

        assertEquals(2, compter("facture_reglements", CENTRE_A));
        assertEquals(2, evenements.recus().size());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(DISTINCT code_reglement) FROM facture_reglements", Integer.class));
    }

    @Test
    void le_code_du_reglement_est_reg_suivi_de_huit_caracteres_hexadecimaux_majuscules() throws Exception {
        UUID facture = factureMille();
        enregistrer(facture, corps(CENTRE_A, "10.00", "2026-02-01", "u"));

        String code = jdbc.queryForObject("SELECT code_reglement FROM facture_reglements", String.class);

        assertEquals(true, code.matches("REG-[0-9A-F]{8}"));
        UUID id = jdbc.queryForObject("SELECT id FROM facture_reglements", UUID.class);
        assertEquals("REG-" + id.toString().substring(0, 8).toUpperCase(), code);
    }

    @Test
    void le_corps_n_a_ni_mode_de_reglement_ni_reference_et_les_champs_inconnus_sont_ignores() throws Exception {
        // KB-FAC-40 : aucun mode (banque, caisse, chèque), aucune référence de pièce ; les champs inconnus sont ignorés.
        UUID facture = factureMille();

        golden("champs_inconnus_ignores_200", enregistrer(facture, json("{'centerId':'" + CENTRE_A
                + "','montant':10,'dateReglement':'2026-02-01','modeReglement':'CHEQUE','reference':'CHQ-1','userId':'u'}")));

        // Nouveau code F4 : auteur = utilisateur de la session (le mode et la référence sont désormais pris en compte, voir
        // ReglementsNouveauCodeCharacterizationIntegrationTest).
        goldenReglements("champs_inconnus_base_nouveau_code", CENTRE_A);
    }

    @Test
    void un_montant_hors_de_la_precision_de_la_colonne_donne_une_erreur_technique() throws Exception {
        UUID facture = factureMille();

        golden("montant_trop_grand", enregistrer(facture, corps(CENTRE_A, "1000000000000.00", "2026-02-01", "u")));

        assertEquals(0, compter("facture_reglements", CENTRE_A));
    }

    @Test
    void les_corps_illisibles_ou_incomplets_sont_refuses() throws Exception {
        UUID facture = factureMille();

        golden("sans_montant", enregistrer(facture, json("{'centerId':'" + CENTRE_A + "'}")));
        golden("sans_centre", enregistrer(facture, json("{'montant':10}")));
        golden("montant_texte", enregistrer(facture, json("{'centerId':'" + CENTRE_A + "','montant':'abc'}")));
        golden("date_illisible", enregistrer(facture, json("{'centerId':'" + CENTRE_A + "','montant':10,'dateReglement':'10/02/2026'}")));
        golden("centre_non_uuid", enregistrer(facture, json("{'centerId':'xyz','montant':10}")));
        golden("json_casse", enregistrer(facture, "{"));
        golden("corps_vide", enregistrer(facture, ""));
        golden("tableau", enregistrer(facture, "[]"));

        assertEquals(0, compter("facture_reglements", CENTRE_A));
    }

    @Test
    void une_facture_dont_le_total_ttc_est_negatif_ne_peut_pas_etre_reglee() throws Exception {
        // L'agrégat refuse un montant de facture négatif (un avoir n'existe pas, KB-FAC-31) : 400, rien n'est enregistré.
        UUID avoir = facture(CENTRE_A, P_AMRANI, "FAC-AVOIR", "2026-01-31", "-50.00", PAYEUR_1, AGENCE_1);

        golden("facture_a_total_negatif_refusee", enregistrer(avoir, corps(CENTRE_A, "10.00", "2026-02-01", "u")));

        assertEquals(0, compter("facture_reglements", CENTRE_A));
    }

    @Test
    void un_identifiant_de_facture_illisible_dans_le_chemin_est_refuse() throws Exception {
        golden("facture_non_uuid", doPost("/pas-un-uuid/paiements", corps(CENTRE_A, "10.00", "2026-02-01", "u"), adminA()));
    }

    @Test
    void la_route_d_enregistrement_est_une_creation_sans_location_et_repond_200() throws Exception {
        UUID facture = factureMille();

        Resp r = enregistrer(facture, corps(CENTRE_A, "1.00", "2026-02-01", "u"));

        assertEquals(200, r.status());
        assertEquals("application/json", r.contentType());
    }
}
