package com.hemodialyse.backend.characterization.stock.referentiel;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation S1 — articles : {@code POST|GET /articles}, {@code PUT|GET /articles/{id}},
 * {@code PATCH /articles/{id}/active}, {@code GET /articles/page} (RG-STK-001, 002, 004, 006 à 008). Point d'observation : HTTP
 * et état SQL de {@code articles}. Écrit sur le legacy ({@code StockReferentialRestController}, {@code Article.appliquerFiche},
 * {@code StockReferentialDomainService}, {@code ArticleRepositoryAdapter}) puis rejoué sur le contexte {@code stock}
 * ({@code ReferentielStockControleur}). Écarts décidés (KB-STK-03 à 06, 08 ; KB-STK-07 reproduit) : versions legacy dans {@code _ecarts-legacy/articles}.
 */
class ArticlesCharacterizationIntegrationTest extends ReferentielStockCharacterizationSupport {

    /**
     * Fiche telle que l'écran l'envoie : les quatre booléens sont toujours présents (sans eux, le legacy répondait 500,
     * KB-STK-03 corrigé : {@code false} par défaut, verrouillé par {@link #la_creation_sans_les_booleens_de_la_fiche_les_met_a_faux}).
     */
    private static String fiche(UUID centre, String code, String extra) {
        return json("{'centerId':'" + centre + "','code':'" + code + "','libelle':'Libelle " + code + "','unite':'piece',"
                + "'gereParLot':false,'peremptionObligatoire':false,'produitDangereux':false,'dechetDasri':false"
                + (extra == null ? "" : "," + extra) + "}");
    }

    @Test
    void la_creation_sans_les_booleens_de_la_fiche_les_met_a_faux() throws Exception {
        golden("creation_sans_booleens_200",
                doPost("/articles", json("{'centerId':'" + CENTRE_A + "','code':'NB','libelle':'L','unite':'u'}"), adminA()));
        assertEquals(1, count("articles", CENTRE_A));
    }

    private static String ficheComplete(UUID centre) {
        return json("{'centerId':'" + centre + "','code':' EPO4000 ','libelle':' Epoetine alfa 4000 UI ','dci':'epoetine alfa',"
                + "'formeGalenique':'solution injectable','codeBarres':'3400930000001','referenceFabricant':'REF-1',"
                + "'unite':'seringue','uniteAchat':'boite','coefficientAchat':6,'dosageParUnite':4000,'uniteDosage':'UI',"
                + "'prixAchat':1250.5,'seuilAlerte':10,'stockMax':120,'gereParLot':true,'peremptionObligatoire':true,"
                + "'conditionConservation':'REFRIGERE','produitDangereux':false,'dechetDasri':true,'typeTraitementAnemie':'EPO',"
                + "'compteStock':'31100','compteCharge':' 60110 '}");
    }

    @Override
    protected String theme() {
        return "articles";
    }

    // ------------------------------------------------------------------ création

    @Test
    void la_creation_complete_enregistre_la_fiche_nettoyee_avec_stock_et_pmp_a_zero_et_actif() throws Exception {
        golden("creation_complete_200", doPost("/articles", ficheComplete(CENTRE_A), adminA()));
        goldenArticles("creation_complete_etat_base", CENTRE_A);
    }

    @Test
    void la_creation_minimale_ne_demande_que_code_libelle_et_unite() throws Exception {
        golden("creation_minimale_200", doPost("/articles", fiche(CENTRE_A, "DIAL-FX60", null), adminA()));
        goldenArticles("creation_minimale_etat_base", CENTRE_A);
    }

    @Test
    void la_fiche_ne_permet_pas_de_saisir_le_stock_ni_le_pmp() throws Exception {
        golden("creation_avec_stock_et_pmp_ignores_200",
                doPost("/articles", fiche(CENTRE_A, "IGN", "'stockQuantity':50,'pmpCourant':9.5,'active':false"), adminA()));
    }

    private static final String BOOLEENS = ",'gereParLot':false,'peremptionObligatoire':false,'produitDangereux':false,'dechetDasri':false";

    @Test
    void les_champs_obligatoires_et_les_bornes_de_forme_sont_controles() throws Exception {
        golden("refus_code_absent", doPost("/articles", json("{'centerId':'" + CENTRE_A + "','libelle':'L','unite':'u'" + BOOLEENS + "}"), adminA()));
        golden("refus_code_blanc", doPost("/articles", fiche(CENTRE_A, "   ", null), adminA()));
        golden("refus_libelle_absent", doPost("/articles", json("{'centerId':'" + CENTRE_A + "','code':'C','unite':'u'" + BOOLEENS + "}"), adminA()));
        golden("refus_unite_absente", doPost("/articles", json("{'centerId':'" + CENTRE_A + "','code':'C','libelle':'L'" + BOOLEENS + "}"), adminA()));
        golden("refus_centre_absent", doPost("/articles", json("{'code':'C','libelle':'L','unite':'u'" + BOOLEENS + "}"), adminA()));
        golden("refus_code_trop_long", doPost("/articles", fiche(CENTRE_A, "X".repeat(51), null), adminA()));
        assertEquals(0, count("articles", CENTRE_A));
    }

    @Test
    void les_invariants_de_la_fiche_sont_proteges_par_l_article() throws Exception {
        golden("refus_dosage_sans_unite", doPost("/articles", fiche(CENTRE_A, "D1", "'dosageParUnite':4000"), adminA()));
        golden("refus_unite_de_dosage_sans_dosage", doPost("/articles", fiche(CENTRE_A, "D2", "'uniteDosage':'UI'"), adminA()));
        golden("refus_dosage_nul", doPost("/articles", fiche(CENTRE_A, "D3", "'dosageParUnite':0,'uniteDosage':'UI'"), adminA()));
        golden("refus_coefficient_nul", doPost("/articles", fiche(CENTRE_A, "C1", "'coefficientAchat':0"), adminA()));
        golden("refus_prix_negatif", doPost("/articles", fiche(CENTRE_A, "P1", "'prixAchat':-1"), adminA()));
        golden("refus_seuil_negatif", doPost("/articles", fiche(CENTRE_A, "P2", "'seuilAlerte':-1"), adminA()));
        golden("refus_stock_max_inferieur_au_seuil", doPost("/articles", fiche(CENTRE_A, "P3", "'seuilAlerte':10,'stockMax':5"), adminA()));
        golden("stock_max_sans_seuil_negatif_compare_a_zero",
                doPost("/articles", fiche(CENTRE_A, "P4", "'stockMax':0"), adminA()));
        golden("refus_compte_invalide", doPost("/articles", fiche(CENTRE_A, "K1", "'compteStock':'311-00'"), adminA()));
        golden("refus_compte_trop_long_forme", doPost("/articles", fiche(CENTRE_A, "K2", "'compteCharge':'" + "6".repeat(21) + "'"), adminA()));
        golden("refus_condition_inconnue", doPost("/articles", fiche(CENTRE_A, "E1", "'conditionConservation':'TIEDE'"), adminA()));
        golden("refus_type_anemie_inconnu", doPost("/articles", fiche(CENTRE_A, "E2", "'typeTraitementAnemie':'VITAMINE'"), adminA()));
        goldenArticles("invariants_etat_base", CENTRE_A);
    }

    @Test
    void le_code_est_unique_par_centre_sans_tenir_compte_de_la_casse() throws Exception {
        assertEquals(200, doPost("/articles", fiche(CENTRE_A, "ABC", null), adminA()).status());
        golden("refus_code_existant_casse_differente", doPost("/articles", fiche(CENTRE_A, "abc", null), adminA()));
        golden("refus_code_existant_avec_espaces", doPost("/articles", fiche(CENTRE_A, " ABC ", null), adminA()));
        golden("meme_code_autre_centre_200", doPost("/articles", fiche(CENTRE_B, "ABC", null), adminB()));
        assertEquals(1, count("articles", CENTRE_A));
    }

    // ------------------------------------------------------------------ modification

    @Test
    void la_modification_remplace_la_fiche_et_garde_stock_pmp_et_activite() throws Exception {
        UUID id = article(CENTRE_A, "ART_A", "OLD", "Ancien", null, null, false, null, "12.5", "3.25");
        golden("modification_200", doPut("/articles/" + id, fiche(CENTRE_A, "NEW", "'seuilAlerte':2,'dci':'dci'"), adminA()));
        goldenArticles("modification_etat_base", CENTRE_A);
    }

    @Test
    void la_modification_garde_son_propre_code_mais_refuse_celui_d_un_autre_article() throws Exception {
        UUID a = article(CENTRE_A, "ART_A", "AAA", "A", null, null, true, null, "0", "0");
        article(CENTRE_A, "ART_B", "BBB", "B", null, null, true, null, "0", "0");
        golden("modification_meme_code_casse_differente_200", doPut("/articles/" + a, fiche(CENTRE_A, "aaa", null), adminA()));
        golden("refus_modification_vers_code_d_un_autre", doPut("/articles/" + a, fiche(CENTRE_A, "bbb", null), adminA()));
    }

    @Test
    void la_modification_d_un_article_inconnu_ou_d_un_autre_centre() throws Exception {
        UUID b = article(CENTRE_B, "ART_B", "B1", "B", null, null, true, null, "0", "0");
        golden("refus_modification_article_inconnu", doPut("/articles/" + UUID.randomUUID(), fiche(CENTRE_A, "X", null), adminA()));
        golden("refus_modification_article_d_un_autre_centre", doPut("/articles/" + b, fiche(CENTRE_A, "X", null), adminA()));
        golden("refus_modification_corps_autre_centre_que_la_session", doPut("/articles/" + b, fiche(CENTRE_B, "X", null), adminA()));
        goldenArticles("autre_centre_inchange", CENTRE_B);
    }

    // ------------------------------------------------------------------ lecture d'un article

    @Test
    void la_lecture_d_un_article_par_identifiant() throws Exception {
        UUID a = article(CENTRE_A, "ART_A", "A1", "Article A", "dci a", "123", true, "FER_INJECTABLE", "4", "1.5");
        UUID b = article(CENTRE_B, "ART_B", "B1", "Article B", null, null, true, null, "0", "0");
        golden("lecture_200", doGet("/articles/" + a + "?centerId=" + CENTRE_A, adminA()));
        golden("lecture_sans_centre_utilise_la_session_200", doGet("/articles/" + a, as(CENTRE_A, "INFIRMIER")));
        golden("lecture_par_medecin_200", doGet("/articles/" + a, as(CENTRE_A, "MEDECIN")));
        golden("refus_lecture_article_inconnu", doGet("/articles/" + UUID.randomUUID(), adminA()));
        golden("refus_lecture_article_d_un_autre_centre", doGet("/articles/" + b, adminA()));
        golden("refus_lecture_centre_autre_que_la_session", doGet("/articles/" + b + "?centerId=" + CENTRE_B, adminA()));
    }

    // ------------------------------------------------------------------ activation

    @Test
    void la_desactivation_et_la_reactivation_d_un_article() throws Exception {
        UUID a = article(CENTRE_A, "ART_A", "A1", "Article A", null, null, true, null, "7", "2");
        golden("desactivation_200", doPatch("/articles/" + a + "/active?active=false", adminA()));
        golden("reactivation_200", doPatch("/articles/" + a + "/active?active=true&centerId=" + CENTRE_A, adminA()));
        golden("refus_activation_sans_parametre", doPatch("/articles/" + a + "/active", adminA()));
        golden("refus_activation_article_inconnu", doPatch("/articles/" + UUID.randomUUID() + "/active?active=false", adminA()));
        goldenArticles("activation_etat_base", CENTRE_A);
    }

    // ------------------------------------------------------------------ liste et recherche paginée

    private void catalogue() {
        article(CENTRE_A, "ART_C", "C-003", "Compresse sterile", "coton", "3400000000003", true, null, "100", "0.2");
        article(CENTRE_A, "ART_A", "A-001", "Aiguille fistule", null, "3400000000001", true, null, "50", "1.1");
        article(CENTRE_A, "ART_E", "E-005", "Epoetine 4000", "epoetine alfa", null, true, "EPO", "6", "40");
        article(CENTRE_A, "ART_F", "F-006", "Fer saccharose", "fer", null, false, "FER_INJECTABLE", "0", "12");
        article(CENTRE_A, "ART_B", "b-002", "Bicarbonate", null, "3400000000002", false, null, "3", "5");
        article(CENTRE_B, "ART_Z", "Z-999", "Article du centre B", "coton", null, true, null, "1", "1");
    }

    @Test
    void la_liste_des_articles_du_centre_est_triee_par_code_actifs_et_inactifs() throws Exception {
        catalogue();
        golden("liste_200", doGet("/articles?centerId=" + CENTRE_A, adminA()));
        golden("liste_epo_200", doGet("/articles?centerId=" + CENTRE_A + "&typeTraitementAnemie=EPO", as(CENTRE_A, "MEDECIN")));
        golden("liste_fer_200", doGet("/articles?centerId=" + CENTRE_A + "&typeTraitementAnemie=FER_INJECTABLE", adminA()));
        golden("liste_type_vide_ignore_200", doGet("/articles?centerId=" + CENTRE_A + "&typeTraitementAnemie=", adminA()));
        golden("refus_liste_type_inconnu", doGet("/articles?centerId=" + CENTRE_A + "&typeTraitementAnemie=VITAMINE", adminA()));
        golden("liste_sans_centre_utilise_la_session_200", doGet("/articles", adminA()));
    }

    @Test
    void la_liste_d_un_autre_centre_que_celui_de_la_session() throws Exception {
        catalogue();
        // KB-STK-04 corrigé : le centre demandé est confronté à celui de la session (403)
        golden("refus_liste_centre_B_par_admin_A", doGet("/articles?centerId=" + CENTRE_B, adminA()));
    }

    @Test
    void la_recherche_paginee_porte_sur_code_libelle_dci_et_code_barres() throws Exception {
        catalogue();
        golden("page_defaut_200", doGet("/articles/page?centerId=" + CENTRE_A, adminA()));
        golden("page_sans_centre_utilise_la_session_200", doGet("/articles/page", adminA()));
        golden("recherche_code_casse_200", doGet("/articles/page?q=c-00", adminA()));
        golden("recherche_libelle_200", doGet("/articles/page?q=SACCHAROSE", adminA()));
        golden("recherche_dci_200", doGet("/articles/page?q=coton", adminA()));
        golden("recherche_code_barres_200", doGet("/articles/page?q=0000000002", adminA()));
        golden("recherche_espaces_200", doGet("/articles/page?q=%20%20epo%20", adminA()));
        golden("recherche_actifs_200", doGet("/articles/page?active=true", adminA()));
        golden("recherche_inactifs_200", doGet("/articles/page?active=false", adminA()));
        golden("page_2_taille_2_200", doGet("/articles/page?page=1&size=2", adminA()));
        golden("taille_nulle_ramenee_a_1_200", doGet("/articles/page?size=0", adminA()));
        golden("page_negative_ramenee_a_0_200", doGet("/articles/page?page=-3&size=2", adminA()));
        golden("taille_500_ramenee_a_200_200", doGet("/articles/page?size=500", adminA()));
        golden("refus_page_autre_centre_que_la_session", doGet("/articles/page?centerId=" + CENTRE_B, adminA()));
    }

    // ------------------------------------------------------------------ rôles

    @Test
    void les_roles_d_ecriture_et_de_lecture() throws Exception {
        UUID a = article(CENTRE_A, "ART_A", "A1", "A", null, null, true, null, "0", "0");
        golden("creation_pharmacien_200", doPost("/articles", fiche(CENTRE_A, "PH1", null), as(CENTRE_A, "PHARMACIEN")));
        golden("refus_creation_infirmier", doPost("/articles", fiche(CENTRE_A, "IN1", null), as(CENTRE_A, "INFIRMIER")));
        golden("refus_creation_medecin", doPost("/articles", fiche(CENTRE_A, "ME1", null), as(CENTRE_A, "MEDECIN")));
        golden("refus_modification_secretaire", doPut("/articles/" + a, fiche(CENTRE_A, "S1", null), as(CENTRE_A, "SECRETAIRE")));
        golden("refus_activation_infirmier", doPatch("/articles/" + a + "/active?active=false", as(CENTRE_A, "INFIRMIER")));
        golden("refus_page_infirmier", doGet("/articles/page", as(CENTRE_A, "INFIRMIER")));
        golden("refus_page_medecin", doGet("/articles/page", as(CENTRE_A, "MEDECIN")));
        golden("liste_infirmier_200", doGet("/articles?centerId=" + CENTRE_A, as(CENTRE_A, "INFIRMIER")));
        golden("refus_liste_secretaire", doGet("/articles?centerId=" + CENTRE_A, as(CENTRE_A, "SECRETAIRE")));
        golden("refus_anonyme", doGetAnonymous("/articles?centerId=" + CENTRE_A));
    }
}









