package com.hemodialyse.backend.characterization.comptabilite.referentiel;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation C1 — compte client des payeurs : {@code GET /api/v1/comptabilite/payeurs} et
 * {@code PUT /api/v1/comptabilite/payeurs/{payeurId}/compte}. Décrit le comportement ACTUEL, défauts compris (voir
 * .ddd/safety-net/known-bugs.md, KB-CPT-01 et suivants).
 */
class ComptesPayeursCharacterizationIntegrationTest extends ReferentielCharacterizationSupport {

    private static String compte(String numero) {
        return json("{'compte':" + (numero == null ? "null" : "'" + numero + "'") + "}");
    }

    @Override
    protected String theme() {
        return "comptes-payeurs";
    }

    private String route(UUID payeur) {
        return "/payeurs/" + payeur + "/compte";
    }

    // ------------------------------------------------------------------ liste des payeurs

    @Test
    void les_payeurs_du_centre_sont_listes_par_nom_puis_code_avec_leur_compte_client_quand_il_existe() throws Exception {
        comptePayeur(CENTRE_A, CNAS, "411210");
        Resp r = doGet("/payeurs", adminA());
        assertEquals(200, r.status());
        assertEquals(3, json(r).get("total").asInt());
        golden("liste_payeurs_du_centre", r);
    }

    @Test
    void la_liste_des_payeurs_est_paginee_avec_une_taille_bornee_a_100_et_une_page_minimale_de_0() throws Exception {
        golden("liste_page_1_taille_2", doGet("/payeurs?page=1&size=2", adminA()));
        golden("liste_page_hors_limites", doGet("/payeurs?page=9&size=2", adminA()));
        golden("liste_taille_0_devient_1", doGet("/payeurs?size=0", adminA()));
        golden("liste_taille_negative_devient_1", doGet("/payeurs?size=-4", adminA()));
        golden("liste_page_negative_devient_0", doGet("/payeurs?page=-3&size=2", adminA()));
        golden("liste_taille_au_dela_de_100_ramenee_a_100", doGet("/payeurs?size=100000", adminA()));
        golden("liste_taille_illisible_500", doGet("/payeurs?size=abc", adminA()));
    }

    @Test
    void la_recherche_des_payeurs_ignore_la_casse_et_les_espaces_de_bord_et_porte_sur_le_code_et_le_nom() throws Exception {
        golden("recherche_par_code", doGet("/payeurs?recherche=casnos", adminA()));
        golden("recherche_par_nom_majuscules", doGet("/payeurs?recherche=MUTUELLE", adminA()));
        golden("recherche_espaces_de_bord", doGet("/payeurs?recherche=%20%20alger%20", adminA()));
        golden("recherche_vide_aucun_filtre", doGet("/payeurs?recherche=", adminA()));
        golden("recherche_blanche_aucun_filtre", doGet("/payeurs?recherche=%20%20", adminA()));
        golden("recherche_sans_resultat", doGet("/payeurs?recherche=zzz", adminA()));
        golden("recherche_joker_underscore_correspond_a_tout_caractere", doGet("/payeurs?recherche=mut_1", adminA()));
        golden("recherche_joker_pourcent_correspond_a_tout", doGet("/payeurs?recherche=%25", adminA()));
        golden("recherche_ne_porte_pas_sur_le_compte", doGet("/payeurs?recherche=411500", adminA()));
    }

    @Test
    void la_liste_des_payeurs_est_bornee_au_centre_de_la_session() throws Exception {
        golden("isolement_centre_b_ne_voit_que_ses_payeurs", doGet("/payeurs", adminB()));
        golden("isolement_centre_sans_payeur_liste_vide", doGet("/payeurs", as(CENTRE_VIDE, "ADMIN")));
        golden("isolement_centre_demande_autre_403", doGet("/payeurs?centerId=" + CENTRE_B, adminA()));
        golden("isolement_centre_propre_explicite_accepte", doGet("/payeurs?centerId=" + CENTRE_A + "&size=1", adminA()));
    }

    // ------------------------------------------------------------------ affectation d'un compte (RG-CPT-019)

    @Test
    void affecter_un_compte_du_plan_de_depart_enregistre_la_ligne_du_payeur_sans_materialiser_le_plan() throws Exception {
        Resp r = doPut(route(CNAS), compte("411500"), adminA());
        assertEquals(200, r.status());
        golden("affecter_compte_nominal_200", r);
        goldenComptesPayeurs("affecter_compte_nominal_etat_base", CENTRE_A);
        assertEquals(0, count("comptes_comptables", CENTRE_A));
        golden("affecter_compte_nominal_liste_apres", doGet("/payeurs", adminA()));
    }

    @Test
    void affecter_un_autre_compte_remplace_celui_du_payeur_et_deux_payeurs_peuvent_partager_un_compte() throws Exception {
        assertEquals(200, doPut(route(CNAS), compte("411500"), adminA()).status());
        golden("remplacer_compte_200", doPut(route(CNAS), compte("411100"), adminA()));
        golden("partager_compte_avec_un_autre_payeur_200", doPut(route(MUTUELLE), compte("411100"), adminA()));
        assertEquals(2, count("comptes_payeurs", CENTRE_A));
        goldenComptesPayeurs("remplacer_compte_etat_base", CENTRE_A);
    }

    @Test
    void le_compte_est_rogne_et_un_compte_vide_blanc_null_ou_absent_rend_au_payeur_le_compte_par_defaut() throws Exception {
        golden("compte_espaces_de_bord_rognes", doPut(route(CNAS), compte("  411500  "), adminA()));
        goldenComptesPayeurs("compte_rogne_etat_base", CENTRE_A);
        golden("compte_vide_retire_200", doPut(route(CNAS), compte(""), adminA()));
        assertEquals(0, count("comptes_payeurs", CENTRE_A));
        assertEquals(200, doPut(route(CNAS), compte("411500"), adminA()).status());
        golden("compte_blanc_retire_200", doPut(route(CNAS), compte("   "), adminA()));
        assertEquals(200, doPut(route(CNAS), compte("411500"), adminA()).status());
        golden("compte_null_retire_200", doPut(route(CNAS), compte(null), adminA()));
        assertEquals(200, doPut(route(CNAS), compte("411500"), adminA()).status());
        golden("compte_absent_corps_objet_vide_retire_200", doPut(route(CNAS), "{}", adminA()));
        assertEquals(0, count("comptes_payeurs", CENTRE_A));
        golden("retirer_le_compte_d_un_payeur_sans_compte_200_idempotent", doPut(route(MUTUELLE), compte(""), adminA()));
    }

    @Test
    void un_compte_hors_plan_ou_desactive_est_refuse_en_compte_inconnu_et_la_casse_compte() throws Exception {
        golden("compte_hors_plan_de_depart_422", doPut(route(CNAS), compte("411210"), adminA()));
        // plan enregistré par le centre : compte actif, compte désactivé, casse
        compte(CENTRE_A, "ABC", "Compte A", true);
        compte(CENTRE_A, "OFF", "Compte désactivé", false);
        golden("compte_du_plan_enregistre_accepte_200", doPut(route(CNAS), compte("ABC"), adminA()));
        golden("compte_desactive_422_inconnu", doPut(route(MUTUELLE), compte("OFF"), adminA()));
        golden("compte_casse_differente_normalise_200_nouveau_code", doPut(route(MUTUELLE), compte("abc"), adminA()));
        golden("compte_du_plan_de_depart_absent_du_plan_enregistre_422", doPut(route(MUTUELLE), compte("411500"), adminA()));
        goldenComptesPayeurs("compte_refuse_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void un_compte_du_parametrage_herite_est_accepte_dans_le_plan_de_depart() throws Exception {
        mapping(CENTRE_A, "7061", "4111", "411500", "512", "530", "44571");
        golden("compte_du_parametrage_herite_accepte", doPut(route(CNAS), compte("7061"), adminA()));
        golden("compte_du_parametrage_herite_inconnu_422", doPut(route(MUTUELLE), compte("7062"), adminA()));
    }

    @Test
    void un_compte_mal_forme_est_refuse_en_400_et_un_compte_de_plus_de_20_caracteres_en_erreur_de_validation() throws Exception {
        golden("compte_tiret_400", doPut(route(CNAS), compte("41-1"), adminA()));
        golden("compte_espace_interne_400", doPut(route(CNAS), compte("41 1"), adminA()));
        golden("compte_accentue_400", doPut(route(CNAS), compte("41é"), adminA()));
        golden("compte_20_caracteres_inconnu_422", doPut(route(CNAS), compte("12345678901234567890"), adminA()));
        golden("compte_21_caracteres_erreur_de_validation_400", doPut(route(CNAS), compte("123456789012345678901"), adminA()));
        golden("compte_nombre_json_coerce_en_chaine", doPut(route(CNAS), json("{'compte':411500}"), adminA()));
        golden("compte_tableau_json_500", doPut(route(CNAS), json("{'compte':['411500']}"), adminA()));
        assertEquals(1, count("comptes_payeurs", CENTRE_A));
        goldenComptesPayeurs("compte_mal_forme_etat_base", CENTRE_A);
    }

    @Test
    void les_corps_illisibles_sont_refuses_sans_toucher_aux_comptes() throws Exception {
        golden("corps_absent_500", doPutSansCorps(route(CNAS), adminA()));
        golden("corps_json_illisible_500", doPut(route(CNAS), "{pas du json", adminA()));
        golden("corps_tableau_500", doPut(route(CNAS), "[]", adminA()));
        golden("proprietes_inconnues_ignorees", doPut(route(CNAS), json("{'compte':'411500','nom':'Autre nom','code':'X'}"), adminA()));
        golden("liste_nom_et_code_du_payeur_inchanges", doGet("/payeurs?recherche=cnas", adminA()));
        assertEquals(1, count("comptes_payeurs", CENTRE_A));
    }

    // ------------------------------------------------------------------ payeur introuvable, isolation

    @Test
    void un_payeur_inconnu_ou_d_un_autre_centre_est_refuse_en_payeur_introuvable_avant_toute_validation_du_compte() throws Exception {
        golden("payeur_inconnu_422", doPut(route(PAYEUR_INCONNU), compte("411500"), adminA()));
        golden("payeur_d_un_autre_centre_422", doPut(route(PAYEUR_B), compte("411500"), adminA()));
        golden("payeur_inconnu_et_compte_mal_forme_dit_payeur_introuvable", doPut(route(PAYEUR_INCONNU), compte("41-1"), adminA()));
        golden("payeur_inconnu_et_compte_hors_plan_dit_payeur_introuvable", doPut(route(PAYEUR_INCONNU), compte("999999"), adminA()));
        golden("payeur_inconnu_retrait_422", doPut(route(PAYEUR_INCONNU), compte(""), adminA()));
        golden("payeur_du_centre_b_depuis_le_centre_b_200", doPut(route(PAYEUR_B), compte("411500"), adminB()));
        golden("payeur_du_centre_a_depuis_le_centre_b_422", doPut(route(CNAS), compte("411500"), adminB()));
        assertEquals(0, count("comptes_payeurs", CENTRE_A));
        assertEquals(1, count("comptes_payeurs", CENTRE_B));
    }

    @Test
    void un_identifiant_de_payeur_illisible_est_une_erreur_interne_500_et_un_centre_demande_different_est_refuse_en_403() throws Exception {
        golden("payeur_identifiant_illisible_500", doPut("/payeurs/pas-un-uuid/compte", compte("411500"), adminA()));
        golden("centre_autre_affectation_403", doPut(route(CNAS) + "?centerId=" + CENTRE_B, compte("411500"), adminA()));
        golden("centre_propre_explicite_accepte", doPut(route(CNAS) + "?centerId=" + CENTRE_A, compte("411500"), adminA()));
        assertEquals(1, count("comptes_payeurs", CENTRE_A));
        assertEquals(0, count("comptes_payeurs", CENTRE_B));
    }

    @Test
    void la_ligne_d_un_payeur_vers_un_compte_absent_du_plan_est_listee_telle_quelle() throws Exception {
        comptePayeur(CENTRE_A, CNAS, "999999");
        golden("compte_hors_plan_en_base_liste_tel_quel", doGet("/payeurs?recherche=cnas", adminA()));
    }

    // ------------------------------------------------------------------ autorisations

    @Test
    void la_consultation_et_l_affectation_des_comptes_de_payeurs_sont_reservees_a_admin() throws Exception {
        golden("autorisation_secretaire_lecture_403", doGet("/payeurs", secretaireA()));
        golden("autorisation_secretaire_affectation_403", doPut(route(CNAS), compte("411500"), secretaireA()));
        golden("autorisation_infirmier_lecture_403", doGet("/payeurs", as(CENTRE_A, "INFIRMIER")));
        golden("autorisation_medecin_seul_lecture_403_role_scope", doGet("/payeurs", as(CENTRE_A, "MEDECIN")));
        golden("autorisation_direction_lecture_403_role_scope", doGet("/payeurs", as(CENTRE_A, "DIRECTION")));
        golden("autorisation_superadmin_lecture_403_role_scope", doGet("/payeurs", as(CENTRE_A, "SUPERADMIN")));
        golden("autorisation_anonyme_lecture", doGetAnonymous("/payeurs"));
        golden("autorisation_admin_et_secretaire_200", doGet("/payeurs?size=1", as(CENTRE_A, "ADMIN", "SECRETAIRE")));
        assertEquals(0, count("comptes_payeurs", CENTRE_A));
    }

    @Test
    void les_methodes_et_routes_absentes_repondent_en_erreur_sans_rien_modifier() throws Exception {
        golden("route_post_payeurs_absente", doRequest("POST", "/payeurs", adminA()));
        golden("route_get_un_payeur_absente", doGet("/payeurs/" + CNAS, adminA()));
        golden("route_get_compte_d_un_payeur_absente", doGet(route(CNAS), adminA()));
        golden("route_delete_compte_d_un_payeur_absente", doDelete(route(CNAS), adminA()));
        golden("route_put_sans_compte_absente", doPut("/payeurs/" + CNAS, compte("411500"), adminA()));
        assertEquals(0, count("comptes_payeurs", CENTRE_A));
    }

    // ------------------------------------------------------------------ parcours

    @Test
    void parcours_complet_ajouter_un_payeur_lui_affecter_un_compte_puis_le_retirer() throws Exception {
        payeur(NOUVEAU_PAYEUR, CENTRE_A, "ASSUR-1", "Assurance Z");
        golden("parcours_1_nouveau_payeur_sans_compte", doGet("/payeurs?recherche=assur", adminA()));
        golden("parcours_2_compte_inconnu_refuse", doPut(route(NOUVEAU_PAYEUR), compte("411210"), adminA()));
        assertEquals(200, doPut("/comptes/411210", json("{'libelle':'Clients — Assurance Z','actif':true}"), adminA()).status());
        golden("parcours_3_affecter_le_compte", doPut(route(NOUVEAU_PAYEUR), compte("411210"), adminA()));
        golden("parcours_4_liste_avec_compte", doGet("/payeurs?recherche=assur", adminA()));
        golden("parcours_5_le_compte_n_est_plus_desactivable", doPut("/comptes/411210",
                json("{'libelle':'Clients — Assurance Z','actif':false}"), adminA()));
        golden("parcours_6_retirer_le_compte", doPut(route(NOUVEAU_PAYEUR), compte(""), adminA()));
        golden("parcours_7_liste_sans_compte", doGet("/payeurs?recherche=assur", adminA()));
        goldenComptesPayeurs("parcours_etat_final_base", CENTRE_A);
        jdbc.update("DELETE FROM centre_payeur WHERE id = ?", NOUVEAU_PAYEUR);
    }
}
