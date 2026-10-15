package com.hemodialyse.backend.characterization.comptabilite.ecritures;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * Filet de caractérisation C3 — consultation des écritures ({@code GET /api/v1/comptabilite/ecritures}) : filtres
 * (période sur la date d'écriture, journal, statut), pagination, tri, forme d'une écriture, isolation de centre,
 * autorisations, validations et lectures impossibles. L'état observé est celui que les générateurs (C5, C6) et la saisie
 * (C4) produisent ; ici les écritures sont insérées en SQL à identifiants fixes.
 */
class ConsultationDesEcrituresCharacterizationIntegrationTest extends EcrituresCharacterizationSupport {

    private static final String TOUT = "from=2025-01-01&to=2026-12-31";

    @Override
    protected String theme() {
        return "consultation";
    }

    /**
     * Six écritures du centre A (brouillon, validée, exportée, banque, année précédente, date de pièce décalée) + une du centre B.
     */
    private void jeuDeDonnees() {
        vente(1, "VE-2026-000001", "2026-03-10", "BROUILLON", "100.00");
        vente(2, "VE-2026-000002", "2026-03-15", "VALIDEE", "200.00");
        vente(3, "VE-2026-000003", "2026-03-15", "EXPORTEE", "300.00");
        ecriture(4, CENTRE_A, "BQ", "BQ-2026-000001", "2026-03-20", "2026-03-20", "VALIDEE", "Règlement", null, null,
                L.d("512", "50.00"), L.c("411100", "50.00"));
        vente(5, "VE-2025-000007", "2025-12-31", "VALIDEE", "70.00");
        ecriture(6, CENTRE_A, "VE", "VE-2026-000004", "2026-04-01", "2026-03-31", "VALIDEE", "Date de pièce décalée", null,
                null, L.d("411100", "80.00"), L.c("706", "80.00"));
        vente(7, CENTRE_B, "VE", "VE-2026-000001", "2026-03-10", "VALIDEE", "999.00");
    }

    private void liste(String nom, String requete) throws Exception {
        golden(nom, doGet("/ecritures?" + requete, adminA()));
    }

    // ------------------------------------------------------------------ tri, forme, période

    @Test
    void la_liste_est_triee_par_date_d_ecriture_decroissante_puis_numero_de_piece_decroissant() throws Exception {
        jeuDeDonnees();
        liste("liste_complete_tri_date_puis_numero_decroissants_200", TOUT);
    }

    @Test
    void une_ecriture_expose_ses_dates_son_numero_son_statut_son_total_debit_et_ses_lignes_dans_l_ordre_de_leur_id() throws Exception {
        UUID tiers = CNAS;
        ecriture(1, CENTRE_A, "VE", "VE-2026-000001", "2026-03-10", "2026-03-10", "VALIDEE", "  Fact. A  ", null, null,
                L.d("411500", "1190.00").libelle("Facture A").tiers(tiers), L.c("706", "1000.00"), L.c("44571", "190.00"));
        goldenOrdreDesLignes("ecriture_forme_complete_lignes_ordre_id_200", doGet("/ecritures?" + TOUT, adminA()));
    }

    @Test
    void la_periode_porte_sur_la_date_d_ecriture_bornes_incluses_et_non_sur_la_date_de_piece() throws Exception {
        jeuDeDonnees();
        liste("periode_un_seul_jour_31_decembre_200", "from=2025-12-31&to=2025-12-31");
        liste("periode_un_seul_jour_deux_ecritures_meme_date_200", "from=2026-03-15&to=2026-03-15");
        liste("periode_sans_ecriture_200", "from=2026-03-16&to=2026-03-19");
        liste("periode_inversee_vide_200", "from=2026-04-30&to=2026-03-01");
        liste("periode_date_de_piece_hors_periode_non_retenue_200", "from=2026-03-31&to=2026-03-31");
        liste("periode_date_d_ecriture_retenue_et_date_de_piece_affichee_200", "from=2026-04-01&to=2026-04-01");
    }

    @Test
    void le_total_compte_toutes_les_pages_et_la_taille_par_defaut_est_vingt() throws Exception {
        for (int i = 1; i <= 23; i++) {
            vente(i, String.format("VE-2026-%06d", i), "2026-03-10", "VALIDEE", "10.00");
        }
        liste("pagination_page_0_taille_par_defaut_200", TOUT);
        liste("pagination_page_1_taille_par_defaut_200", TOUT + "&page=1");
        liste("pagination_page_au_dela_du_total_vide_200", TOUT + "&page=2");
    }

    // ------------------------------------------------------------------ pagination

    @Test
    void la_pagination_decoupe_la_liste_triee_et_refuse_les_tailles_et_pages_negatives_ou_nulles() throws Exception {
        jeuDeDonnees();
        liste("pagination_taille_2_page_0_200", TOUT + "&size=2&page=0");
        liste("pagination_taille_2_page_1_200", TOUT + "&size=2&page=1");
        liste("pagination_taille_2_page_2_derniere_200", TOUT + "&size=2&page=2");
        liste("pagination_taille_2_page_3_vide_200", TOUT + "&size=2&page=3");
        liste("pagination_taille_1000_200", TOUT + "&size=1000");
        liste("pagination_taille_zero", TOUT + "&size=0");
        liste("pagination_taille_negative", TOUT + "&size=-1");
        liste("pagination_page_negative", TOUT + "&page=-1");
        liste("pagination_taille_non_numerique", TOUT + "&size=abc");
        liste("pagination_page_non_numerique", TOUT + "&page=x");
    }

    // ------------------------------------------------------------------ filtres journal et statut

    @Test
    void le_filtre_de_journal_est_normalise_en_majuscules_et_un_journal_vide_ou_blanc_est_ignore() throws Exception {
        jeuDeDonnees();
        liste("journal_banque_200", TOUT + "&journalCode=BQ");
        liste("journal_minuscules_normalise_200", TOUT + "&journalCode=bq");
        liste("journal_avec_espaces_rogne_200", TOUT + "&journalCode=%20BQ%20");
        liste("journal_inconnu_liste_vide_200", TOUT + "&journalCode=ZZ");
        liste("journal_vide_ignore_200", TOUT + "&journalCode=");
        liste("journal_blanc_ignore_200", TOUT + "&journalCode=%20%20");
    }

    @Test
    void un_journal_hors_format_est_refuse_en_400() throws Exception {
        jeuDeDonnees();
        liste("journal_avec_tiret_400", TOUT + "&journalCode=V-E");
        liste("journal_onze_caracteres_400", TOUT + "&journalCode=ABCDEFGHIJK");
        liste("journal_dix_caracteres_inconnu_200", TOUT + "&journalCode=ABCDEFGHIJ");
        liste("journal_accentue_400", TOUT + "&journalCode=%C3%A9");
    }

    @Test
    void le_filtre_de_statut_est_exact_sensible_a_la_casse_et_refuse_un_statut_inconnu_en_400() throws Exception {
        jeuDeDonnees();
        liste("statut_brouillon_200", TOUT + "&statut=BROUILLON");
        liste("statut_validee_200", TOUT + "&statut=VALIDEE");
        liste("statut_exportee_200", TOUT + "&statut=EXPORTEE");
        liste("statut_minuscules_400", TOUT + "&statut=validee");
        liste("statut_inconnu_400", TOUT + "&statut=ANNULEE");
        liste("statut_vide_400", TOUT + "&statut=");
        liste("journal_et_statut_combines_200", TOUT + "&journalCode=VE&statut=VALIDEE");
        liste("journal_et_statut_sans_resultat_200", TOUT + "&journalCode=BQ&statut=EXPORTEE");
    }

    // ------------------------------------------------------------------ paramètres

    @Test
    void les_dates_sont_obligatoires_au_format_iso_et_valides() throws Exception {
        jeuDeDonnees();
        golden("sans_from", doGet("/ecritures?to=2026-12-31", adminA()));
        golden("sans_to", doGet("/ecritures?from=2025-01-01", adminA()));
        golden("sans_aucune_date", doGet("/ecritures", adminA()));
        liste("from_mal_forme", "from=abc&to=2026-12-31");
        liste("to_mal_forme", "from=2025-01-01&to=31-12-2026");
        liste("date_inexistante_30_fevrier", "from=2026-02-30&to=2026-12-31");
        liste("date_sans_zero_non_iso", "from=2026-3-1&to=2026-12-31");
        liste("from_vide", "from=&to=2026-12-31");
    }

    // ------------------------------------------------------------------ centre

    @Test
    void chaque_centre_ne_lit_que_ses_propres_ecritures() throws Exception {
        jeuDeDonnees();
        golden("centre_a_lit_ses_ecritures_200", doGet("/ecritures?" + TOUT + "&size=100", adminA()));
        golden("centre_b_lit_les_siennes_200", doGet("/ecritures?" + TOUT, adminB()));
        golden("centre_sans_ecriture_liste_vide_200", doGet("/ecritures?" + TOUT, as(CENTRE_VIDE, "ADMIN")));
    }

    @Test
    void le_centre_demande_doit_etre_celui_de_la_session() throws Exception {
        jeuDeDonnees();
        golden("centre_autre_demande_403", doGet("/ecritures?" + TOUT + "&centerId=" + CENTRE_B, adminA()));
        golden("centre_propre_demande_200", doGet("/ecritures?" + TOUT + "&centerId=" + CENTRE_A + "&size=1", adminA()));
        golden("centre_illisible", doGet("/ecritures?" + TOUT + "&centerId=pas-un-uuid", adminA()));
        golden("centre_vide_ignore_200", doGet("/ecritures?" + TOUT + "&centerId=", adminA()));
    }

    // ------------------------------------------------------------------ rôles

    @Test
    void la_consultation_est_permise_a_l_admin_et_a_la_secretaire_seulement() throws Exception {
        jeuDeDonnees();
        String q = "/ecritures?" + TOUT + "&size=1";
        golden("autorisation_admin_200", doGet(q, adminA()));
        golden("autorisation_secretaire_200", doGet(q, secretaireA()));
        golden("autorisation_infirmier_403", doGet(q, as(CENTRE_A, "INFIRMIER")));
        golden("autorisation_medecin_seul_role_scope_403", doGet(q, as(CENTRE_A, "MEDECIN")));
        golden("autorisation_direction_role_scope_403", doGet(q, as(CENTRE_A, "DIRECTION")));
        golden("autorisation_superadmin_role_scope_403", doGet(q, as(CENTRE_A, "SUPERADMIN")));
        golden("autorisation_anonyme_403", doGetAnonymous(q));
    }

    // ------------------------------------------------------------------ nature de l'écriture

    @Test
    void une_piece_saisie_et_une_extourne_sont_marquees_saisie_alors_qu_une_ecriture_du_systeme_ne_l_est_pas() throws Exception {
        UUID modele = UUID.fromString("99996300-0000-0000-0003-000000000001");
        UUID autre = UUID.fromString("99996300-0000-0000-0001-000000000099");
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        ecriture(2, CENTRE_A, "OD", "OD-2026-000001", "2026-03-11", "2026-03-11", "VALIDEE", "Pièce saisie", ecr(2), modele,
                L.d("6", "5.00"), L.c("4", "5.00"));
        ecriture(3, CENTRE_A, "OD", "OD-2026-000002", "2026-03-12", "2026-03-11", "VALIDEE", "Extourne OD-2026-000001", autre,
                modele, L.d("4", "5.00"), L.c("6", "5.00"));
        goldenOrdreDesLignes("saisie_extourne_et_systeme_200", doGet("/ecritures?" + TOUT, adminA()));
    }

    @Test
    void un_code_de_journal_enregistre_en_minuscules_est_relu_en_majuscules() throws Exception {
        ecriture(1, CENTRE_A, "ve", "ve-2026-000001", "2026-03-10", "2026-03-10", "VALIDEE", "Journal minuscule", null, null,
                L.d("411100", "10.00"), L.c("706", "10.00"));
        golden("journal_en_minuscules_en_base_relu_en_majuscules_numero_inchange_200", doGet("/ecritures?" + TOUT, adminA()));
        golden("journal_en_minuscules_filtre_majuscules_200", doGet("/ecritures?" + TOUT + "&journalCode=VE", adminA()));
    }

    // ------------------------------------------------------------------ données illisibles

    @Test
    void une_ecriture_desequilibree_en_base_fait_echouer_la_page_qui_la_contient_en_422() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        ecriture(2, CENTRE_A, "VE", "VE-2026-000002", "2026-03-11", "2026-03-11", "VALIDEE", "Déséquilibrée", null, null,
                L.d("411100", "10.00"), L.c("706", "9.99"));
        golden("lecture_desequilibre_en_base_page_entiere_422", doGet("/ecritures?" + TOUT, adminA()));
        golden("lecture_desequilibre_page_0_saine_200", doGet("/ecritures?" + TOUT + "&size=1&page=1", adminA()));
        golden("lecture_desequilibre_page_de_la_fautive_422", doGet("/ecritures?" + TOUT + "&size=1&page=0", adminA()));
    }

    @Test
    void une_ecriture_a_une_seule_ligne_un_montant_vide_ou_un_statut_inconnu_en_base_est_illisible_en_400() throws Exception {
        ecriture(1, CENTRE_A, "VE", "VE-2026-000001", "2026-03-10", "2026-03-10", "VALIDEE", "Une seule ligne", null, null,
                L.d("411100", "0.00"));
        golden("lecture_une_seule_ligne_en_base", doGet("/ecritures?" + TOUT, adminA()));
        cleanupEcritures();
        ecriture(2, CENTRE_A, "VE", "VE-2026-000002", "2026-03-10", "2026-03-10", "VALIDEE", "Montant vide", null, null,
                new L("411100", null, "0.00", "Débit nul", null), L.c("706", "0.00"));
        golden("lecture_montant_nul_en_base", doGet("/ecritures?" + TOUT, adminA()));
        cleanupEcritures();
        vente(3, "VE-2026-000003", "2026-03-10", "ANNULEE", "10.00");
        golden("lecture_statut_inconnu_en_base", doGet("/ecritures?" + TOUT, adminA()));
    }

    private void corrompre(String nom, String sql) throws Exception {
        cleanupEcritures();
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        jdbc.update(sql);
        golden(nom, doGet("/ecritures?" + TOUT, adminA()));
        goldenSql(nom + "_etat_en_base", "SELECT numero_piece, statut FROM ecritures_comptables WHERE center_id = '" + CENTRE_A + "'");
    }

    @Test
    void une_ligne_ou_un_numero_invalide_en_base_rend_toute_la_page_illisible_en_400() throws Exception {
        corrompre("lecture_compte_blanc_en_base", "UPDATE lignes_ecriture SET compte_scf = '  ' WHERE compte_scf = '706'");
        corrompre("lecture_debit_negatif_en_base", "UPDATE lignes_ecriture SET montant_debit = -5.00 WHERE compte_scf = '411100'");
        corrompre("lecture_credit_negatif_en_base", "UPDATE lignes_ecriture SET montant_credit = -5.00 WHERE compte_scf = '706'");
        corrompre("lecture_credit_nul_en_base", "UPDATE lignes_ecriture SET montant_credit = NULL WHERE compte_scf = '706'");
        corrompre("lecture_numero_de_piece_blanc_en_base", "UPDATE ecritures_comptables SET numero_piece = ' '");
        corrompre("lecture_journal_hors_format_en_base", "UPDATE ecritures_comptables SET journal_code = 'V-E'");
        corrompre("lecture_axe_analytique_sans_libelle_en_base", "UPDATE lignes_ecriture SET axes_analytiques = 'CENTRE:' WHERE compte_scf = '706'");
        corrompre("lecture_axe_analytique_sans_code_en_base", "UPDATE lignes_ecriture SET axes_analytiques = ':Centre' WHERE compte_scf = '706'");
    }

    @Test
    void une_ecriture_sans_libelle_ni_axe_se_lit_avec_un_libelle_vide_et_un_libelle_est_rogne() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        jdbc.update("UPDATE ecritures_comptables SET libelle = NULL");
        jdbc.update("UPDATE lignes_ecriture SET libelle_ligne = '  rogné  ' WHERE compte_scf = '706'");
        jdbc.update("UPDATE lignes_ecriture SET libelle_ligne = NULL WHERE compte_scf = '411100'");
        golden("libelles_nuls_et_a_rogner_en_base_200", doGet("/ecritures?" + TOUT, adminA()));
    }

    @Test
    void les_montants_a_plus_de_deux_decimales_ne_peuvent_pas_etre_en_base_et_un_zero_exact_reste_a_deux_decimales() throws Exception {
        ecriture(1, CENTRE_A, "VE", "VE-2026-000001", "2026-03-10", "2026-03-10", "VALIDEE", "Montants", null, null,
                L.d("411100", "1234567.89"), L.c("706", "1234567.89"));
        golden("montants_grands_deux_decimales_200", doGet("/ecritures?" + TOUT, adminA()));
    }

    // ------------------------------------------------------------------ routes absentes

    @Test
    void les_routes_non_declarees_sur_les_ecritures_repondent_en_500() throws Exception {
        jeuDeDonnees();
        golden("route_post_ecritures", doRequest("POST", "/ecritures", adminA()));
        golden("route_put_ecritures_id", doRequest("PUT", "/ecritures/" + ecr(1), adminA()));
        golden("route_delete_ecritures_id", doRequest("DELETE", "/ecritures/" + ecr(1), adminA()));
        golden("route_get_ecriture_par_id", doRequest("GET", "/ecritures/" + ecr(1), adminA()));
        golden("route_get_periodes", doRequest("GET", "/periodes", adminA()));
        golden("route_get_periodes_cloturer", doRequest("GET", "/periodes/cloturer", adminA()));
        golden("route_delete_periodes_cloturer", doRequest("DELETE", "/periodes/cloturer", adminA()));
        golden("route_post_ecritures_id_exporter", doRequest("POST", "/ecritures/export", adminA()));
        golden("route_get_ecritures_id_valider", doRequest("GET", "/ecritures/" + ecr(1) + "/valider", adminA()));
        golden("route_post_ecritures_id_invalider", doRequest("POST", "/ecritures/" + ecr(1) + "/invalider", adminA()));
    }
}
