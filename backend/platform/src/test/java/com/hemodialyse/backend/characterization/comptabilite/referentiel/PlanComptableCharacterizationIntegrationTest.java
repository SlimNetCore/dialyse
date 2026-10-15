package com.hemodialyse.backend.characterization.comptabilite.referentiel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation C1 — plan comptable : {@code GET/PUT/DELETE /api/v1/comptabilite/comptes[/{numero}]}.
 * Décrit le comportement ACTUEL, défauts compris (voir .ddd/safety-net/known-bugs.md, KB-CPT-01 et suivants).
 */
class PlanComptableCharacterizationIntegrationTest extends ReferentielCharacterizationSupport {

    private static String corps(String libelle, boolean actif) {
        return json("{'libelle':'" + libelle + "','actif':" + actif + "}");
    }

    @Override
    protected String theme() {
        return "plan-comptable";
    }

    // ------------------------------------------------------------------ plan SCF de départ (RG-CPT-018)

    @Test
    void un_centre_sans_plan_voit_le_plan_scf_de_depart_de_20_comptes_trie_par_numero_sans_rien_enregistrer() throws Exception {
        Resp r = doGet("/comptes?size=100", adminA());
        assertEquals(200, r.status());
        assertEquals(20, json(r).get("total").asInt());
        golden("plan_depart_liste_complete", r);
        assertEquals(0, count("comptes_comptables", CENTRE_A));
    }

    @Test
    void la_liste_par_defaut_est_une_page_de_20_et_les_parametres_de_page_sont_bornes() throws Exception {
        golden("plan_depart_page_defaut", doGet("/comptes", adminA()));
        golden("plan_depart_page_2_taille_5", doGet("/comptes?page=2&size=5", adminA()));
        golden("plan_depart_page_hors_limites", doGet("/comptes?page=99&size=5", adminA()));
        golden("plan_depart_taille_0_devient_1", doGet("/comptes?size=0", adminA()));
        golden("plan_depart_taille_negative_devient_1", doGet("/comptes?size=-3", adminA()));
        golden("plan_depart_page_negative_devient_0", doGet("/comptes?page=-1&size=2", adminA()));
        golden("plan_depart_taille_au_dela_de_500_acceptee", doGet("/comptes?size=100000&page=1", adminA()));
        golden("plan_depart_taille_illisible_500", doGet("/comptes?size=abc", adminA()));
        golden("plan_depart_centre_demande_illisible_500", doGet("/comptes?centerId=pas-un-uuid", adminA()));
    }

    @Test
    void la_recherche_du_plan_de_depart_ignore_la_casse_les_espaces_de_bord_et_porte_sur_le_numero_et_le_libelle() throws Exception {
        golden("plan_depart_recherche_libelle_banq", doGet("/comptes?recherche=banq", adminA()));
        golden("plan_depart_recherche_libelle_majuscules", doGet("/comptes?recherche=BANQUE", adminA()));
        golden("plan_depart_recherche_espaces_de_bord", doGet("/comptes?recherche=%20%20clients%20", adminA()));
        golden("plan_depart_recherche_numero_partiel", doGet("/comptes?recherche=411", adminA()));
        golden("plan_depart_recherche_vide_equivaut_a_aucun_filtre", doGet("/comptes?recherche=&size=3", adminA()));
        golden("plan_depart_recherche_blanche_equivaut_a_aucun_filtre", doGet("/comptes?recherche=%20%20&size=3", adminA()));
        golden("plan_depart_recherche_sans_resultat", doGet("/comptes?recherche=zzz", adminA()));
        golden("plan_depart_recherche_joker_percent_est_litteral", doGet("/comptes?recherche=%25", adminA()));
        golden("plan_depart_filtre_actifs_sans_effet", doGet("/comptes?actifs=true&size=3", adminA()));
        golden("plan_depart_actifs_illisible_500", doGet("/comptes?actifs=peut-etre", adminA()));
    }

    @Test
    void le_plan_de_depart_est_complete_des_comptes_deja_utilises_par_le_parametrage_du_centre() throws Exception {
        // paramétrage hérité d'avant le plan comptable : 7061 (ventes) et 4111 (clients) ne sont pas dans le plan SCF
        mapping(CENTRE_A, "7061", "4111", "411500", "512", "530", "44571");
        Resp r = doGet("/comptes?recherche=compte&size=100", adminA());
        assertEquals(200, r.status());
        golden("plan_depart_complete_des_comptes_du_parametrage", r);
        golden("plan_depart_complete_liste_integrale_ordre", doGet("/comptes?size=100", adminA()));
        assertEquals(0, count("comptes_comptables", CENTRE_A));
    }

    @Test
    void la_premiere_modification_enregistre_tout_le_plan_de_depart_comme_celui_du_centre() throws Exception {
        Resp r = doPut("/comptes/411210", corps("Clients — CNAS", true), adminA());
        assertEquals(200, r.status());
        golden("enregistrer_compte_nominal_200", r);
        assertEquals(21, count("comptes_comptables", CENTRE_A));
        goldenComptes("enregistrer_compte_nominal_etat_base", CENTRE_A);
        // la liste ne montre plus le plan de départ mais le plan enregistré, avec le nouveau compte
        golden("enregistrer_compte_nominal_liste_apres", doGet("/comptes?recherche=411&size=100", adminA()));
    }

    @Test
    void un_compte_inconnu_du_centre_est_cree_par_le_meme_appel_qui_modifie_un_compte_existant() throws Exception {
        assertEquals(200, doPut("/comptes/411210", corps("Clients CNAS", true), adminA()).status());
        Resp modif = doPut("/comptes/411210", corps("Clients CNAS (révisé)", true), adminA());
        assertEquals(200, modif.status());
        golden("enregistrer_compte_existant_met_a_jour", modif);
        assertEquals(21, count("comptes_comptables", CENTRE_A));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM comptes_comptables WHERE center_id = ? AND numero = '411210'",
                Integer.class, CENTRE_A));
        goldenSql("enregistrer_compte_existant_ligne", "SELECT numero, libelle, actif FROM comptes_comptables "
                + "WHERE center_id = '" + CENTRE_A + "' AND numero = '411210'");
    }

    // ------------------------------------------------------------------ formats et validations

    @Test
    void le_numero_de_compte_accepte_1_a_20_lettres_ou_chiffres_sans_normaliser_la_casse() throws Exception {
        golden("numero_1_caractere_accepte", doPut("/comptes/7", corps("Un", true), adminA()));
        golden("numero_20_caracteres_accepte", doPut("/comptes/12345678901234567890", corps("Vingt", true), adminA()));
        golden("numero_21_caracteres_refuse_400", doPut("/comptes/123456789012345678901", corps("Vingt et un", true), adminA()));
        golden("numero_minuscules_normalise_en_majuscules_nouveau_code", doPut("/comptes/abc9", corps("Minuscules", true), adminA()));
        golden("numero_majuscules_meme_compte_nouveau_code", doPut("/comptes/ABC9", corps("Majuscules", true), adminA()));
        golden("numero_tiret_refuse_400", doPut("/comptes/41-1", corps("Tiret", true), adminA()));
        golden("numero_point_refuse_400", doPut("/comptes/41.1", corps("Point", true), adminA()));
        golden("numero_underscore_refuse_400", doPut("/comptes/41_1", corps("Underscore", true), adminA()));
        golden("numero_accentue_refuse_400", doPut("/comptes/41%C3%A9", corps("Accent", true), adminA()));
        golden("numero_espaces_de_bord_rognes", doPut("/comptes/%20411999%20", corps("Rogne", true), adminA()));
        golden("numero_espace_interne_refuse_400", doPut("/comptes/41%201", corps("Espace interne", true), adminA()));
        goldenComptes("formats_numero_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void le_libelle_est_obligatoire_rogne_et_limite_a_150_caracteres() throws Exception {
        golden("libelle_absent_400", doPut("/comptes/900", json("{'actif':true}"), adminA()));
        golden("libelle_vide_400", doPut("/comptes/900", corps("", true), adminA()));
        golden("libelle_blanc_400", doPut("/comptes/900", corps("   ", true), adminA()));
        golden("libelle_null_400", doPut("/comptes/900", json("{'libelle':null,'actif':true}"), adminA()));
        golden("libelle_rogne_a_l_enregistrement", doPut("/comptes/901", corps("  Compte rogné  ", true), adminA()));
        golden("libelle_150_caracteres_accepte", doPut("/comptes/902", corps("é".repeat(150), true), adminA()));
        golden("libelle_151_caracteres_refuse_400", doPut("/comptes/903", corps("é".repeat(151), true), adminA()));
        golden("libelle_151_dont_espaces_de_bord_refuse_400_avant_rognage",
                doPut("/comptes/904", corps(" ".repeat(10) + "x".repeat(141), true), adminA()));
        goldenSql("libelle_etat_base", "SELECT numero, LENGTH(libelle) AS longueur, actif FROM comptes_comptables "
                + "WHERE center_id = '" + CENTRE_A + "' AND numero IN ('900','901','902','903','904') ORDER BY numero");
        assertEquals("Compte rogné", jdbc.queryForObject("SELECT libelle FROM comptes_comptables WHERE center_id = ? "
                + "AND numero = '901'", String.class, CENTRE_A));
    }

    @Test
    void un_compte_enregistre_sans_actif_ou_avec_actif_null_est_refuse_en_erreur_interne_500() throws Exception {
        Resp r = doPut("/comptes/905", json("{'libelle':'Sans drapeau'}"), adminA());
        assertEquals(400, r.status());
        golden("actif_absent_400_nouveau_code", r);
        golden("actif_null_400_nouveau_code", doPut("/comptes/906", json("{'libelle':'Null','actif':null}"), adminA()));
        golden("actif_chaine_true_coerce", doPut("/comptes/907", json("{'libelle':'Chaine','actif':'true'}"), adminA()));
        golden("actif_texte_illisible_500", doPut("/comptes/908", json("{'libelle':'Texte','actif':'oui'}"), adminA()));
        golden("actif_nombre_1_coerce_ou_refuse", doPut("/comptes/909", json("{'libelle':'Nombre','actif':1}"), adminA()));
        golden("proprietes_inconnues_ignorees", doPut("/comptes/910",
                json("{'libelle':'Extra','actif':true,'numero':'999','centerId':'" + CENTRE_B + "'}"), adminA()));
        goldenComptes("actif_etat_base", CENTRE_A);
    }

    @Test
    void les_corps_illisibles_sont_refuses_sans_toucher_au_plan() throws Exception {
        golden("corps_absent_500", doPutSansCorps("/comptes/911", adminA()));
        golden("corps_json_illisible_500", doPut("/comptes/911", "{pas du json", adminA()));
        golden("corps_tableau_500", doPut("/comptes/911", "[]", adminA()));
        golden("corps_objet_vide_400_nouveau_code", doPut("/comptes/911", "{}", adminA()));
        golden("corps_type_de_contenu_texte_500", send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put(BASE + "/comptes/911").contentType("text/plain").content("x").with(adminA())));
        assertEquals(0, count("comptes_comptables", CENTRE_A));
    }

    // ------------------------------------------------------------------ désactivation et suppression (RG-CPT-018)

    @Test
    void un_compte_utilise_par_le_parametrage_ne_se_desactive_pas_et_rien_n_est_enregistre() throws Exception {
        Resp r = doPut("/comptes/512", corps("Banque", false), adminA());
        assertEquals(422, r.status());
        golden("desactiver_compte_du_mapping_422", r);
        // le refus annule aussi l'enregistrement du plan de départ (transaction)
        assertEquals(0, count("comptes_comptables", CENTRE_A));
        // tous les comptes du paramétrage par défaut sont protégés
        for (String c : new String[]{"706", "411100", "411500", "530", "44571", "322", "602", "408", "757", "657"}) {
            assertEquals(422, doPut("/comptes/" + c, corps("x", false), adminA()).status(), c);
        }
        assertEquals(0, count("comptes_comptables", CENTRE_A));
    }

    @Test
    void un_compte_libre_se_desactive_se_reactive_et_reste_dans_la_liste() throws Exception {
        golden("desactiver_compte_libre_200", doPut("/comptes/626", corps("Frais postaux", false), adminA()));
        assertEquals(20, count("comptes_comptables", CENTRE_A));
        golden("desactiver_compte_libre_idempotent_200", doPut("/comptes/626", corps("Frais postaux", false), adminA()));
        golden("liste_actifs_seulement_sans_le_compte_desactive", doGet("/comptes?actifs=true&recherche=626", adminA()));
        golden("liste_tous_avec_le_compte_desactive", doGet("/comptes?recherche=626", adminA()));
        golden("reactiver_compte_200", doPut("/comptes/626", corps("Frais postaux", true), adminA()));
        goldenComptes("desactivation_etat_base", CENTRE_A);
    }

    @Test
    void un_compte_porte_par_un_payeur_ne_se_desactive_ni_ne_se_supprime() throws Exception {
        assertEquals(200, doPut("/comptes/411210", corps("Clients CNAS", true), adminA()).status());
        assertEquals(200, doPut("/payeurs/" + CNAS + "/compte", json("{'compte':'411210'}"), adminA()).status());
        golden("desactiver_compte_d_un_payeur_422", doPut("/comptes/411210", corps("Clients CNAS", false), adminA()));
        golden("supprimer_compte_d_un_payeur_422", doDelete("/comptes/411210", adminA()));
        // la désactivation redevient possible quand le payeur est libéré
        assertEquals(200, doPut("/payeurs/" + CNAS + "/compte", json("{'compte':''}"), adminA()).status());
        golden("desactiver_compte_apres_liberation_du_payeur_200", doPut("/comptes/411210", corps("Clients CNAS", false), adminA()));
    }

    @Test
    void un_compte_porte_par_un_modele_de_piece_ne_se_desactive_ni_ne_se_supprime() throws Exception {
        String modele = json("{'code':'loyer','libelle':'Loyer','journal':'AC','actif':true,'lignes':["
                + "{'sens':'DEBIT','compte':'613','libelle':'Loyer'},{'sens':'CREDIT','compte':'401'}]}");
        assertEquals(201, doPost("/modeles", modele, adminA()).status());
        golden("desactiver_compte_d_un_modele_422", doPut("/comptes/613", corps("Locations", false), adminA()));
        golden("supprimer_compte_d_un_modele_422", doDelete("/comptes/401", adminA()));
        golden("compte_hors_modele_se_supprime_204", doDelete("/comptes/615", adminA()));
        goldenComptes("modele_etat_base", CENTRE_A);
    }

    @Test
    void un_compte_rattache_a_un_article_est_utilise_nouveau_code() throws Exception {
        compte(CENTRE_A, "3101", "Stock article", true);
        compte(CENTRE_A, "6011", "Charge article", true);
        jdbc.update("INSERT INTO articles (id, active, center_id, code, gere_par_lot, libelle, seuil_alerte, stock_quantity, "
                        + "unite, compte_stock, compte_charge) VALUES (?, TRUE, ?, 'ART-C1', FALSE, 'Article', 0, 0, 'U', '3101', '6011')",
                java.util.UUID.randomUUID(), CENTRE_A);
        golden("compte_de_stock_d_un_article_desactivation_422_nouveau_code", doPut("/comptes/3101", corps("Stock article", false), adminA()));
        golden("compte_de_charge_d_un_article_suppression_422_nouveau_code", doDelete("/comptes/6011", adminA()));
        goldenComptes("article_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void un_compte_qui_porte_des_ecritures_se_desactive_mais_ne_se_supprime_pas() throws Exception {
        compte(CENTRE_A, "ZZ1", "Compte avec écritures", true);
        compte(CENTRE_A, "ZZ2", "Autre compte avec écritures", true);
        ecriture(CENTRE_A, "VE", "VE-2026-000001", "ZZ1", "ZZ2");
        golden("supprimer_compte_avec_ecritures_422", doDelete("/comptes/ZZ1", adminA()));
        golden("desactiver_compte_avec_ecritures_200", doPut("/comptes/ZZ1", corps("Compte avec écritures", false), adminA()));
        golden("supprimer_compte_desactive_avec_ecritures_422", doDelete("/comptes/ZZ1", adminA()));
        goldenComptes("ecritures_etat_base", CENTRE_A);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM lignes_ecriture l JOIN ecritures_comptables e "
                + "ON e.id = l.ecriture_id WHERE e.center_id = ?", Integer.class, CENTRE_A));
    }

    @Test
    void les_ecritures_d_un_autre_centre_ne_protegent_pas_le_compte() throws Exception {
        compte(CENTRE_A, "ZZ1", "Compte A", true);
        compte(CENTRE_B, "ZZ1", "Compte B", true);
        ecriture(CENTRE_B, "VE", "VE-2026-000001", "ZZ1", "ZZ1");
        golden("ecritures_autre_centre_suppression_204", doDelete("/comptes/ZZ1", adminA()));
        goldenComptes("ecritures_autre_centre_etat_a", CENTRE_A);
        goldenComptes("ecritures_autre_centre_etat_b", CENTRE_B);
        golden("ecritures_autre_centre_suppression_422_chez_b", doDelete("/comptes/ZZ1", adminB()));
    }

    @Test
    void l_utilisation_par_le_parametrage_est_controlee_avant_les_ecritures() throws Exception {
        compte(CENTRE_A, "706", "Prestations", true);
        ecriture(CENTRE_A, "VE", "VE-2026-000001", "706", "706");
        // 706 est à la fois dans le mapping par défaut et porte des écritures : le premier motif l'emporte
        golden("compte_utilise_et_avec_ecritures_dit_utilise", doDelete("/comptes/706", adminA()));
    }

    @Test
    void la_suppression_est_physique_et_un_compte_inconnu_ou_mal_forme_est_refuse() throws Exception {
        Resp ok = doDelete("/comptes/626", adminA());
        assertEquals(204, ok.status());
        golden("supprimer_compte_libre_204", ok);
        assertEquals(19, count("comptes_comptables", CENTRE_A));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM comptes_comptables WHERE center_id = ? AND numero = '626'",
                Integer.class, CENTRE_A));
        golden("supprimer_compte_deja_supprime_422_introuvable", doDelete("/comptes/626", adminA()));
        golden("supprimer_compte_jamais_cree_422_introuvable", doDelete("/comptes/98765", adminA()));
        golden("supprimer_numero_mal_forme_400", doDelete("/comptes/41-1", adminA()));
        golden("supprimer_numero_20_plus_1_400", doDelete("/comptes/123456789012345678901", adminA()));
        golden("supprimer_numero_espaces_de_bord_rognes", doDelete("/comptes/%20615%20", adminA()));
        golden("supprimer_la_casse_compte", doDelete("/comptes/ABC", adminA()));
        goldenComptes("suppression_etat_base", CENTRE_A);
    }

    @Test
    void un_refus_de_suppression_sur_un_centre_sans_plan_n_enregistre_pas_le_plan_de_depart() throws Exception {
        assertEquals(422, doDelete("/comptes/98765", adminA()).status());
        assertEquals(0, count("comptes_comptables", CENTRE_A));
        assertEquals(204, doDelete("/comptes/615", adminA()).status());
        assertEquals(19, count("comptes_comptables", CENTRE_A));
    }

    // ------------------------------------------------------------------ plan enregistré : liste, tri, recherche

    @Test
    void un_plan_enregistre_remplace_le_plan_de_depart_et_se_liste_par_numero_avec_filtre_actifs() throws Exception {
        compte(CENTRE_A, "512", "Banque BNA", true);
        compte(CENTRE_A, "5121", "Banque CPA", false);
        compte(CENTRE_A, "10", "Capital", true);
        compte(CENTRE_A, "9", "Neuf", true);
        compte(CENTRE_A, "A1", "Alpha", true);
        golden("plan_enregistre_liste_tri_par_numero", doGet("/comptes", adminA()));
        golden("plan_enregistre_actifs_seulement", doGet("/comptes?actifs=true", adminA()));
        golden("plan_enregistre_recherche_libelle_et_casse", doGet("/comptes?recherche=%20BANQUE%20", adminA()));
        golden("plan_enregistre_recherche_numero_et_actifs", doGet("/comptes?recherche=512&actifs=true", adminA()));
        golden("plan_enregistre_pagination", doGet("/comptes?page=1&size=2", adminA()));
        golden("plan_enregistre_page_hors_limites", doGet("/comptes?page=9&size=2", adminA()));
        golden("plan_enregistre_taille_0_devient_1", doGet("/comptes?size=0", adminA()));
        golden("plan_enregistre_page_negative_devient_0", doGet("/comptes?page=-2&size=2", adminA()));
    }

    @Test
    void sur_un_plan_enregistre_les_jokers_sql_de_la_recherche_ne_sont_pas_echappes() throws Exception {
        compte(CENTRE_A, "41", "Clients", true);
        compte(CENTRE_A, "42", "Personnel", true);
        compte(CENTRE_A, "5A", "Autre", true);
        // sur le plan de départ la même recherche est littérale (golden plan_depart_recherche_joker_percent_est_litteral)
        golden("recherche_joker_underscore_correspond_a_tout_caractere", doGet("/comptes?recherche=4_", adminA()));
        golden("recherche_joker_pourcent_correspond_a_tout", doGet("/comptes?recherche=%25", adminA()));
        golden("recherche_joker_underscore_seul", doGet("/comptes?recherche=_", adminA()));
    }

    @Test
    void une_ligne_de_plan_dont_le_numero_n_a_pas_le_format_fait_echouer_toute_la_liste_en_400() throws Exception {
        compte(CENTRE_A, "41", "Clients", true);
        compte(CENTRE_A, "4_", "Numero hors format", true);
        golden("numero_hors_format_en_base_liste_400", doGet("/comptes", adminA()));
        golden("numero_hors_format_en_base_recherche_qui_l_exclut_200", doGet("/comptes?recherche=clients", adminA()));
    }

    @Test
    void un_plan_enregistre_ne_complete_plus_avec_les_comptes_du_parametrage() throws Exception {
        mapping(CENTRE_A, "7061", "4111", "411500", "512", "530", "44571");
        compte(CENTRE_A, "512", "Banque", true);
        golden("plan_enregistre_ignore_les_comptes_du_parametrage", doGet("/comptes", adminA()));
    }

    // ------------------------------------------------------------------ isolation de centre

    @Test
    void chaque_centre_a_son_propre_plan_et_un_numero_peut_exister_dans_deux_centres() throws Exception {
        assertEquals(200, doPut("/comptes/411210", corps("Clients A", true), adminA()).status());
        golden("isolement_centre_b_voit_son_plan_de_depart", doGet("/comptes?recherche=411210", adminB()));
        golden("isolement_centre_b_cree_le_meme_numero", doPut("/comptes/411210", corps("Clients B", true), adminB()));
        golden("isolement_centre_a_inchange", doGet("/comptes?recherche=411210", adminA()));
        goldenSql("isolement_etat_base", "SELECT center_id, numero, libelle FROM comptes_comptables WHERE numero = '411210' "
                + "AND center_id IN ('" + CENTRE_A + "', '" + CENTRE_B + "') ORDER BY center_id");
        assertEquals(200, doDelete("/comptes/411210", adminB()).status() == 204 ? 200 : 0);
        golden("isolement_centre_a_garde_son_compte", doGet("/comptes?recherche=411210", adminA()));
    }

    @Test
    void un_centre_demande_different_de_celui_de_la_session_est_refuse_en_403_pour_toutes_les_routes() throws Exception {
        Resp lecture = doGet("/comptes?centerId=" + CENTRE_B, adminA());
        assertEquals(403, lecture.status());
        golden("centre_autre_lecture_403", lecture);
        golden("centre_autre_enregistrement_403", doPut("/comptes/900?centerId=" + CENTRE_B, corps("x", true), adminA()));
        golden("centre_autre_suppression_403", doDelete("/comptes/615?centerId=" + CENTRE_B, adminA()));
        golden("centre_propre_explicite_accepte", doGet("/comptes?centerId=" + CENTRE_A + "&size=1", adminA()));
        assertEquals(0, count("comptes_comptables", CENTRE_A));
        assertEquals(0, count("comptes_comptables", CENTRE_B));
    }

    // ------------------------------------------------------------------ autorisations

    @Test
    void la_consultation_est_reservee_a_admin_et_secretaire_et_les_ecritures_a_admin() throws Exception {
        golden("autorisation_secretaire_lecture_200", doGet("/comptes?size=2", secretaireA()));
        Resp put = doPut("/comptes/900", corps("x", true), secretaireA());
        assertEquals(403, put.status());
        golden("autorisation_secretaire_enregistrement_403", put);
        golden("autorisation_secretaire_suppression_403", doDelete("/comptes/615", secretaireA()));
        golden("autorisation_infirmier_lecture_403", doGet("/comptes", as(CENTRE_A, "INFIRMIER")));
        golden("autorisation_medecin_seul_lecture_403_role_scope", doGet("/comptes", as(CENTRE_A, "MEDECIN")));
        golden("autorisation_medecin_et_secretaire_lecture_200", doGet("/comptes?size=1", as(CENTRE_A, "MEDECIN", "SECRETAIRE")));
        golden("autorisation_direction_lecture_403_role_scope", doGet("/comptes", as(CENTRE_A, "DIRECTION")));
        golden("autorisation_superadmin_lecture_403_role_scope", doGet("/comptes", as(CENTRE_A, "SUPERADMIN")));
        golden("autorisation_anonyme_lecture", doGetAnonymous("/comptes"));
        golden("autorisation_anonyme_ecriture", send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put(BASE + "/comptes/900").contentType("application/json").content(corps("x", true))));
        assertEquals(0, count("comptes_comptables", CENTRE_A));
    }

    @Test
    void les_methodes_et_routes_absentes_repondent_en_erreur_sans_rien_modifier() throws Exception {
        golden("route_post_comptes_absente", doRequest("POST", "/comptes", adminA()));
        golden("route_get_un_compte_absente", doGet("/comptes/512", adminA()));
        golden("route_patch_compte_absente", doRequest("PATCH", "/comptes/512", adminA()));
        golden("route_delete_collection_absente", doDelete("/comptes", adminA()));
        golden("route_put_collection_absente", doPut("/comptes", corps("x", true), adminA()));
        assertEquals(0, count("comptes_comptables", CENTRE_A));
    }

    // ------------------------------------------------------------------ parcours

    @Test
    void parcours_complet_creer_modifier_desactiver_lister_et_supprimer_un_compte() throws Exception {
        golden("parcours_1_creer_compte", doPut("/comptes/6811", corps("Dotations aux amortissements", true), adminA()));
        golden("parcours_2_modifier_libelle", doPut("/comptes/6811", corps("Dotations amortissements", true), adminA()));
        golden("parcours_3_desactiver", doPut("/comptes/6811", corps("Dotations amortissements", false), adminA()));
        golden("parcours_4_lister_actifs_recherche", doGet("/comptes?actifs=true&recherche=6811", adminA()));
        golden("parcours_5_lister_tous_recherche", doGet("/comptes?recherche=6811", adminA()));
        golden("parcours_6_supprimer", doDelete("/comptes/6811", adminA()));
        golden("parcours_7_lister_apres_suppression", doGet("/comptes?recherche=6811", adminA()));
        goldenComptes("parcours_etat_final_base", CENTRE_A);
    }
}
