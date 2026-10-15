package com.hemodialyse.backend.characterization.comptabilite.mappingtva;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation C2 — mapping comptable d'un centre : {@code GET /mapping} et {@code PUT /mapping} (comptes
 * de ventes, clients, banque, caisse, TVA collectée, comptes du stock, journal de chaque opération). Point
 * d'observation : HTTP (MockMvc, Spring complet, sécurité réelle + RoleScopeFilter, H2) et état SQL de
 * {@code mapping_comptable}.
 */
class MappingComptableCharacterizationIntegrationTest extends MappingTvaCharacterizationSupport {

    private static final Object RETIRE = new Object();

    @Override
    protected String theme() {
        return "mapping";
    }

    /**
     * Corps de PUT : valeurs de base, surchargées par paires (clé, valeur) ; {@code RETIRE} supprime la clé.
     */
    private String corps(Object... paires) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("compteVentes", "706");
        m.put("compteClientPatient", "411100");
        m.put("compteClientDefaut", "411500");
        m.put("compteBanque", "512");
        m.put("compteCaisse", "530");
        m.put("compteTVACollectee", "44571");
        for (int i = 0; i < paires.length; i += 2) {
            if (paires[i + 1] == RETIRE) {
                m.remove((String) paires[i]);
            } else {
                m.put((String) paires[i], paires[i + 1]);
            }
        }
        return mapper.writeValueAsString(m);
    }

    private String corpsJournaux(String journaux) {
        return corps().replaceFirst("}$", ",\"journaux\":" + journaux.replace('\'', '"') + "}");
    }

    /**
     * 422 {@code JOURNAL_INCONNU} dont l'opération citée n'est pas verrouillée : le contrôle parcourt une
     * {@code Map.copyOf} dont l'ordre d'itération varie d'une JVM à l'autre (KB-CPT-15), donc quand plusieurs
     * opérations sont fautives (ou partagent le journal fautif), l'opération et le code nommés sont l'un d'eux.
     */
    private void journalInconnu(String codesPossibles, Resp r) {
        assertEquals(422, r.status());
        assertEquals("JOURNAL_INCONNU", json(r).path("code").asString());
        org.junit.jupiter.api.Assertions.assertTrue(json(r).path("detail").asString().matches(
                "Le journal (" + codesPossibles + ") choisi pour (VENTE|REGLEMENT_BANQUE|REGLEMENT_CAISSE|STOCK_RECEPTION"
                        + "|STOCK_SORTIE|STOCK_INVENTAIRE) n'existe pas ou est désactivé"), r.raw());
    }

    private void enregistrerLeMappingParDefaut() throws Exception {
        assertEquals(200, doPut("/mapping", corps(), adminA()).status());
    }

    // ------------------------------------------------------------------ lecture et valeurs par défaut

    @Test
    void un_centre_sans_parametrage_lit_le_mapping_par_defaut_sans_rien_enregistrer() throws Exception {
        golden("lecture_sans_ligne_valeurs_par_defaut_200", doGet("/mapping", adminA()));
        assertEquals(0, count("mapping_comptable", CENTRE_A));
    }

    @Test
    void le_mapping_par_defaut_d_un_centre_est_le_meme_pour_tous_les_centres() throws Exception {
        golden("lecture_centre_vide_valeurs_par_defaut_200", doGet("/mapping", as(CENTRE_VIDE, "ADMIN")));
    }

    @Test
    void un_parametrage_herite_sans_stock_ni_journaux_complete_les_valeurs_manquantes_par_defaut() throws Exception {
        mappingHerite(CENTRE_A, "7061", "4111", "4115", "5121", "5301", "44572");
        golden("lecture_ligne_heritee_stock_et_journaux_par_defaut_200", doGet("/mapping", adminA()));
    }

    @Test
    void un_parametrage_herite_sans_compte_de_tva_lit_le_compte_44571() throws Exception {
        mappingHerite(CENTRE_A, "7061", "4111", "4115", "5121", "5301", null);
        golden("lecture_ligne_heritee_sans_tva_44571_200", doGet("/mapping", adminA()));
        mappingHerite(CENTRE_B, "7061", "4111", "4115", "5121", "5301", "   ");
        golden("lecture_ligne_heritee_tva_blanche_44571_200", doGet("/mapping", adminB()));
    }

    @Test
    void les_colonnes_de_stock_et_de_journaux_vides_ou_blanches_valent_leurs_valeurs_par_defaut() throws Exception {
        mappingHerite(CENTRE_A, "706", "411100", "411500", "512", "530", "44571");
        jdbc.update("UPDATE mapping_comptable SET compte_stock = ' ', compte_consommation = '', journal_vente = ' ', "
                + "journal_stock_sortie = '' WHERE center_id = ?", CENTRE_A);
        golden("lecture_colonnes_blanches_valeurs_par_defaut_200", doGet("/mapping", adminA()));
    }

    @Test
    void un_compte_de_ventes_blanc_en_base_fait_echouer_la_lecture_en_400() throws Exception {
        mappingHerite(CENTRE_A, " ", "411100", "411500", "512", "530", "44571");
        golden("lecture_compte_ventes_blanc_en_base_400", doGet("/mapping", adminA()));
    }

    @Test
    void un_journal_en_base_est_normalise_en_majuscules_et_un_journal_hors_format_fait_echouer_la_lecture() throws Exception {
        mappingHerite(CENTRE_A, "706", "411100", "411500", "512", "530", "44571");
        jdbc.update("UPDATE mapping_comptable SET journal_vente = 'od', journal_stock_inventaire = ' bq ' "
                + "WHERE center_id = ?", CENTRE_A);
        golden("lecture_journal_en_minuscules_en_base_normalise_200", doGet("/mapping", adminA()));
        jdbc.update("UPDATE mapping_comptable SET journal_vente = 'V-E' WHERE center_id = ?", CENTRE_A);
        golden("lecture_journal_hors_format_en_base_400", doGet("/mapping", adminA()));
    }

    // ------------------------------------------------------------------ enregistrement nominal

    @Test
    void le_premier_enregistrement_cree_la_ligne_du_centre_et_la_reponse_est_le_mapping_enregistre() throws Exception {
        golden("enregistrement_premier_200", doPut("/mapping", corps(), adminA()));
        goldenMapping("enregistrement_premier_etat_base", CENTRE_A);
        assertEquals(1, count("mapping_comptable", CENTRE_A));
        assertEquals(0, count("comptes_comptables", CENTRE_A), "le plan de départ n'est pas enregistré par le mapping");
        assertEquals(0, count("journaux_comptables", CENTRE_A), "les journaux par défaut ne sont pas enregistrés par le mapping");
    }

    @Test
    void un_second_enregistrement_remplace_la_ligne_sans_en_creer_une_deuxieme() throws Exception {
        enregistrerLeMappingParDefaut();
        golden("enregistrement_second_200", doPut("/mapping", corps("compteVentes", "615", "compteBanque", "401"), adminA()));
        goldenMapping("enregistrement_second_etat_base", CENTRE_A);
        assertEquals(1, count("mapping_comptable", CENTRE_A));
    }

    @Test
    void un_enregistrement_identique_repete_est_idempotent() throws Exception {
        enregistrerLeMappingParDefaut();
        golden("enregistrement_repete_200", doPut("/mapping", corps(), adminA()));
        assertEquals(1, count("mapping_comptable", CENTRE_A));
    }

    @Test
    void un_parametrage_herite_est_mis_a_jour_en_place_et_complete_par_les_valeurs_par_defaut() throws Exception {
        mappingHerite(CENTRE_A, "7061", "4111", "4115", "5121", "5301", "44572");
        golden("enregistrement_sur_ligne_heritee_200", doPut("/mapping", corps(), adminA()));
        goldenMapping("enregistrement_sur_ligne_heritee_etat_base", CENTRE_A);
        assertEquals(1, count("mapping_comptable", CENTRE_A));
    }

    @Test
    void les_comptes_sont_rognes_et_les_memes_comptes_peuvent_servir_a_plusieurs_usages() throws Exception {
        golden("enregistrement_comptes_rognes_200", doPut("/mapping", corps("compteVentes", " 706 ", "compteBanque", "  512"), adminA()));
        golden("enregistrement_meme_compte_pour_tous_les_usages_200", doPut("/mapping",
                corps("compteVentes", "512", "compteClientPatient", "512", "compteClientDefaut", "512", "compteCaisse", "512",
                        "compteTVACollectee", "512"), adminA()));
        goldenMapping("enregistrement_meme_compte_etat_base", CENTRE_A);
    }

    @Test
    void le_compte_de_tva_collectee_facultatif_n_est_pas_controle_et_se_relit_en_44571() throws Exception {
        golden("tva_absente_200", doPut("/mapping", corps("compteTVACollectee", RETIRE), adminA()));
        goldenMapping("tva_absente_etat_base", CENTRE_A);
        golden("tva_absente_relue_44571_200", doGet("/mapping", adminA()));
        golden("tva_vide_200", doPut("/mapping", corps("compteTVACollectee", ""), adminA()));
        golden("tva_blanche_200", doPut("/mapping", corps("compteTVACollectee", "   "), adminA()));
        golden("tva_nulle_200", doPut("/mapping", corps("compteTVACollectee", null), adminA()));
        // un plan enregistré sans 44571 : la TVA absente n'est pas contrôlée, la TVA renseignée l'est
        planDuMappingParDefaut(CENTRE_B);
        jdbc.update("UPDATE comptes_comptables SET actif = FALSE WHERE center_id = ? AND numero = '44571'", CENTRE_B);
        golden("tva_blanche_non_controlee_meme_si_44571_desactive_200", doPut("/mapping", corps("compteTVACollectee", " "), adminB()));
        golden("tva_renseignee_desactivee_422", doPut("/mapping", corps("compteTVACollectee", "44571"), adminB()));
    }

    // ------------------------------------------------------------------ comptes du stock
    // ---- (le plan enregistré doit contenir les comptes du stock même s'ils ne sont pas modifiés)

    @Test
    void les_comptes_du_stock_absents_ou_blancs_conservent_les_valeurs_actuelles() throws Exception {
        golden("stock_personnalise_200", doPut("/mapping", corps("compteStock", "613", "compteConsommation", "615",
                "compteFacturesNonParvenues", "401", "compteBoniInventaire", "631", "compteMaliInventaire", "635"), adminA()));
        golden("stock_absent_conserve_200", doPut("/mapping", corps(), adminA()));
        golden("stock_blanc_conserve_200", doPut("/mapping", corps("compteStock", "  ", "compteConsommation", "",
                "compteFacturesNonParvenues", null), adminA()));
        golden("stock_partiel_remplace_un_seul_200", doPut("/mapping", corps("compteMaliInventaire", " 626 "), adminA()));
        goldenMapping("stock_partiel_etat_base", CENTRE_A);
    }

    @Test
    void il_n_existe_pas_de_retour_aux_comptes_du_stock_par_defaut_autrement_qu_en_les_renvoyant() throws Exception {
        assertEquals(200, doPut("/mapping", corps("compteStock", "613"), adminA()).status());
        golden("stock_defaut_renvoye_explicitement_200", doPut("/mapping", corps("compteStock", "322"), adminA()));
    }

    @Test
    void un_compte_du_stock_hors_plan_est_refuse_meme_s_il_n_est_pas_modifie_quand_le_plan_enregistre_ne_le_contient_pas() throws Exception {
        for (String c : new String[]{"706", "411100", "411500", "512", "530", "44571"}) {
            compte(CENTRE_A, c, "Compte " + c, true);
        }
        golden("plan_enregistre_sans_comptes_du_stock_422", doPut("/mapping", corps(), adminA()));
        assertEquals(0, count("mapping_comptable", CENTRE_A));
    }

    // ------------------------------------------------------------------ contrôle des comptes du plan

    @Test
    void un_compte_hors_plan_de_depart_est_refuse_en_422_et_rien_n_est_enregistre() throws Exception {
        enregistrerLeMappingParDefaut();
        golden("compte_ventes_hors_plan_422", doPut("/mapping", corps("compteVentes", "7099"), adminA()));
        golden("compte_stock_hors_plan_422", doPut("/mapping", corps("compteStock", "999"), adminA()));
        golden("compte_en_minuscules_422", doPut("/mapping", corps("compteVentes", "abc"), adminA()));
        goldenMapping("refus_laisse_la_ligne_inchangee", CENTRE_A);
    }

    @Test
    void plusieurs_comptes_hors_plan_ne_sont_pas_listes_ensemble_et_un_compte_desactive_est_refuse() throws Exception {
        planDuMappingParDefaut(CENTRE_A);
        compte(CENTRE_A, "7061", "Ventes bis", false);
        golden("compte_desactive_422", doPut("/mapping", corps("compteVentes", "7061"), adminA()));
        golden("deux_comptes_hors_plan_422", doPut("/mapping", corps("compteVentes", "7099", "compteBanque", "5999"), adminA()));
        golden("compte_actif_du_plan_enregistre_200", doPut("/mapping", corps("compteVentes", "706"), adminA()));
    }

    @Test
    void le_compte_hors_plan_du_centre_est_juge_sur_le_plan_de_son_propre_centre() throws Exception {
        planDuMappingParDefaut(CENTRE_A);
        planDuMappingParDefaut(CENTRE_B);
        compte(CENTRE_B, "7062", "Ventes ter", true);
        golden("compte_d_un_autre_centre_422", doPut("/mapping", corps("compteVentes", "7062"), adminA()));
        golden("compte_de_son_centre_200", doPut("/mapping", corps("compteVentes", "7062"), adminB()));
    }

    // ------------------------------------------------------------------ validation des champs

    @Test
    void les_cinq_comptes_obligatoires_absents_vides_blancs_ou_nuls_sont_refuses_en_400() throws Exception {
        for (String champ : new String[]{"compteVentes", "compteClientPatient", "compteClientDefaut", "compteBanque", "compteCaisse"}) {
            golden("obligatoire_" + champ + "_absent_400", doPut("/mapping", corps(champ, RETIRE), adminA()));
            golden("obligatoire_" + champ + "_vide_400", doPut("/mapping", corps(champ, ""), adminA()));
            golden("obligatoire_" + champ + "_blanc_400", doPut("/mapping", corps(champ, "   "), adminA()));
            golden("obligatoire_" + champ + "_nul_400", doPut("/mapping", corps(champ, null), adminA()));
        }
        assertEquals(0, count("mapping_comptable", CENTRE_A));
    }

    @Test
    void un_compte_de_20_caracteres_passe_la_validation_et_21_caracteres_sont_refuses() throws Exception {
        golden("compte_20_caracteres_inconnu_du_plan_422", doPut("/mapping", corps("compteVentes", "12345678901234567890"), adminA()));
        golden("compte_21_caracteres_400", doPut("/mapping", corps("compteVentes", "123456789012345678901"), adminA()));
        golden("compte_stock_21_caracteres_400", doPut("/mapping", corps("compteStock", "123456789012345678901"), adminA()));
        golden("compte_tva_21_caracteres_400", doPut("/mapping", corps("compteTVACollectee", "123456789012345678901"), adminA()));
        golden("compte_ventes_21_caracteres_et_vide_400", doPut("/mapping", corps("compteVentes", "123456789012345678901",
                "compteBanque", ""), adminA()));
    }

    @Test
    void les_proprietes_inconnues_du_corps_sont_ignorees() throws Exception {
        golden("propriete_inconnue_ignoree_200", doPut("/mapping",
                corps().replaceFirst("}$", ",\"compteInconnu\":\"x\",\"id\":\"abc\"}"), adminA()));
        goldenMapping("propriete_inconnue_etat_base", CENTRE_A);
    }

    @Test
    void les_corps_illisibles_sont_une_erreur_interne_500() throws Exception {
        golden("corps_absent_500", doPutSansCorps("/mapping", adminA()));
        golden("corps_illisible_500", doPut("/mapping", "{pas du json", adminA()));
        golden("corps_tableau_500", doPut("/mapping", "[]", adminA()));
        golden("corps_nul_500", doPut("/mapping", "null", adminA()));
        golden("corps_texte_500", doPutTexte("/mapping", corps(), adminA()));
        golden("corps_objet_vide_400", doPut("/mapping", "{}", adminA()));
        golden("journaux_de_type_texte_500", doPut("/mapping", corps("journaux", "VE"), adminA()));
        golden("centre_illisible_500", doPut("/mapping", corps("centerId", "pas-un-uuid"), adminA()));
        assertEquals(0, count("mapping_comptable", CENTRE_A));
        golden("compte_de_type_nombre_coerce_200", doPut("/mapping", corps("compteVentes", 706), adminA()));
    }

    // ------------------------------------------------------------------ journaux de chaque opération

    @Test
    void sans_journaux_dans_le_corps_chaque_operation_garde_son_journal_actuel_ou_par_defaut() throws Exception {
        golden("journaux_absents_defauts_200", doPut("/mapping", corps(), adminA()));
        journauxParDefaut(CENTRE_B);
        journal(CENTRE_B, "OD", "Opérations diverses", true);
        assertEquals(200, doPut("/mapping", corps("journaux", java.util.Map.of("VENTE", "OD", "STOCK_SORTIE", "OD")), adminB()).status());
        golden("journaux_absents_conserve_les_journaux_choisis_200", doPut("/mapping", corps("compteVentes", "615"), adminB()));
        golden("journaux_vides_conserve_les_journaux_choisis_200", doPut("/mapping", corps("journaux", java.util.Map.of()), adminB()));
        goldenMapping("journaux_conserves_etat_base", CENTRE_B);
    }

    @Test
    void un_journal_ne_se_choisit_que_pour_les_operations_nommees_et_les_codes_sont_normalises() throws Exception {
        journauxParDefaut(CENTRE_A);
        journal(CENTRE_A, "OD", "Opérations diverses", true);
        golden("journal_d_une_seule_operation_200", doPut("/mapping", corpsJournaux("{'REGLEMENT_CAISSE':'OD'}"), adminA()));
        golden("journal_en_minuscules_normalise_200", doPut("/mapping", corpsJournaux("{'VENTE':'od'}"), adminA()));
        golden("journal_avec_espaces_rogne_200", doPut("/mapping", corpsJournaux("{'STOCK_RECEPTION':' bq '}"), adminA()));
        golden("six_operations_dans_le_meme_journal_200", doPut("/mapping",
                corpsJournaux("{'VENTE':'OD','REGLEMENT_BANQUE':'OD','REGLEMENT_CAISSE':'OD','STOCK_RECEPTION':'OD',"
                        + "'STOCK_SORTIE':'OD','STOCK_INVENTAIRE':'OD'}"), adminA()));
        goldenMapping("six_operations_etat_base", CENTRE_A);
    }

    @Test
    void une_operation_inconnue_ou_un_code_de_journal_mal_forme_est_refuse_en_400() throws Exception {
        golden("operation_inconnue_400", doPut("/mapping", corpsJournaux("{'VENTES':'VE'}"), adminA()));
        golden("operation_en_minuscules_400", doPut("/mapping", corpsJournaux("{'vente':'VE'}"), adminA()));
        golden("journal_nul_400", doPut("/mapping", corpsJournaux("{'VENTE':null}"), adminA()));
        golden("journal_vide_400", doPut("/mapping", corpsJournaux("{'VENTE':''}"), adminA()));
        golden("journal_blanc_400", doPut("/mapping", corpsJournaux("{'VENTE':'  '}"), adminA()));
        golden("journal_11_caracteres_400", doPut("/mapping", corpsJournaux("{'VENTE':'ABCDEFGHIJK'}"), adminA()));
        golden("journal_10_caracteres_inconnu_422", doPut("/mapping", corpsJournaux("{'VENTE':'ABCDEFGHIJ'}"), adminA()));
        golden("journal_avec_tiret_400", doPut("/mapping", corpsJournaux("{'VENTE':'V-E'}"), adminA()));
        golden("journal_avec_accent_400", doPut("/mapping", corpsJournaux("{'VENTE':'VÉ'}"), adminA()));
        golden("operation_inconnue_passe_avant_journal_inconnu", doPut("/mapping", corpsJournaux("{'VENTE':'ZZ','AUTRE':'VE'}"), adminA()));
        assertEquals(0, count("mapping_comptable", CENTRE_A));
    }

    @Test
    void sans_journal_enregistre_les_cinq_journaux_par_defaut_sont_acceptes_et_les_autres_refuses() throws Exception {
        golden("journal_par_defaut_accepte_200", doPut("/mapping", corpsJournaux("{'VENTE':'BQ'}"), adminA()));
        golden("journal_inconnu_422", doPut("/mapping", corpsJournaux("{'VENTE':'OD'}"), adminA()));
        journalInconnu("ZZ|YY", doPut("/mapping",
                corpsJournaux("{'STOCK_INVENTAIRE':'ZZ','REGLEMENT_BANQUE':'YY'}"), adminA()));
    }

    @Test
    void un_journal_desactive_ou_absent_des_journaux_enregistres_est_refuse_meme_s_il_n_est_pas_modifie() throws Exception {
        journal(CENTRE_A, "VE", "Ventes", true);
        journal(CENTRE_A, "BQ", "Banque", true);
        journal(CENTRE_A, "CA", "Caisse", true);
        journal(CENTRE_A, "AC", "Achats", true);
        journal(CENTRE_A, "OD", "Désactivé", false);
        journalInconnu("ST", doPut("/mapping", corps(), adminA()));
        journal(CENTRE_A, "ST", "Stocks", true);
        golden("journaux_enregistres_complets_200", doPut("/mapping", corps(), adminA()));
        golden("journal_desactive_422", doPut("/mapping", corpsJournaux("{'VENTE':'OD'}"), adminA()));
        jdbc.update("UPDATE journaux_comptables SET actif = FALSE WHERE center_id = ? AND code = 'ST'", CENTRE_A);
        journalInconnu("ST", doPut("/mapping", corps(), adminA()));
    }

    @Test
    void le_controle_des_journaux_passe_avant_celui_des_comptes() throws Exception {
        golden("journal_et_compte_invalides_dit_journal_inconnu", doPut("/mapping",
                corps("compteVentes", "7099", "journaux", java.util.Map.of("VENTE", "ZZ")), adminA()));
    }

    // ------------------------------------------------------------------ protections du référentiel par le mapping (C1)

    @Test
    void les_comptes_et_les_journaux_choisis_par_le_mapping_ne_se_desactivent_plus_dans_le_referentiel() throws Exception {
        enregistrerLeMappingParDefaut();
        golden("compte_du_mapping_ne_se_desactive_pas_422", doPut("/comptes/706",
                json("{'libelle':'Prestations','actif':false}"), adminA()));
        golden("journal_du_mapping_ne_se_desactive_pas_422", doPut("/journaux/VE",
                json("{'libelle':'Ventes','actif':false}"), adminA()));
        assertEquals(200, doPut("/mapping", corps("compteVentes", "615"), adminA()).status());
        golden("compte_libere_par_le_mapping_se_desactive_200", doPut("/comptes/706",
                json("{'libelle':'Prestations','actif':false}"), adminA()));
    }

    // ------------------------------------------------------------------ centres

    @Test
    void chaque_centre_a_son_propre_mapping_et_les_autres_ne_sont_pas_modifies() throws Exception {
        enregistrerLeMappingParDefaut();
        assertEquals(200, doPut("/mapping", corps("compteVentes", "615"), adminB()).status());
        golden("centre_a_lit_son_mapping_200", doGet("/mapping", adminA()));
        golden("centre_b_lit_son_mapping_200", doGet("/mapping", adminB()));
        golden("centre_vide_lit_les_valeurs_par_defaut_200", doGet("/mapping", as(CENTRE_VIDE, "ADMIN")));
        assertEquals(1, count("mapping_comptable", CENTRE_A));
        assertEquals(1, count("mapping_comptable", CENTRE_B));
    }

    @Test
    void le_centre_demande_doit_etre_celui_de_la_session_sinon_403() throws Exception {
        golden("lecture_centre_d_un_autre_403", doGet("/mapping?centerId=" + CENTRE_B, adminA()));
        golden("lecture_centre_propre_200", doGet("/mapping?centerId=" + CENTRE_A, adminA()));
        golden("lecture_centre_illisible_500", doGet("/mapping?centerId=pas-un-uuid", adminA()));
        golden("enregistrement_centre_d_un_autre_403", doPut("/mapping", corps("centerId", CENTRE_B.toString()), adminA()));
        golden("enregistrement_centre_propre_200", doPut("/mapping", corps("centerId", CENTRE_A.toString()), adminA()));
        assertEquals(0, count("mapping_comptable", CENTRE_B));
        assertEquals(1, count("mapping_comptable", CENTRE_A));
    }

    // ------------------------------------------------------------------ autorisations

    @Test
    void seul_l_administrateur_lit_et_enregistre_le_mapping() throws Exception {
        golden("autorisation_secretaire_lecture_403", doGet("/mapping", as(CENTRE_A, "SECRETAIRE")));
        golden("autorisation_secretaire_ecriture_403", doPut("/mapping", corps(), as(CENTRE_A, "SECRETAIRE")));
        golden("autorisation_infirmier_lecture_403", doGet("/mapping", as(CENTRE_A, "INFIRMIER")));
        golden("autorisation_infirmier_ecriture_403", doPut("/mapping", corps(), as(CENTRE_A, "INFIRMIER")));
        golden("autorisation_medecin_seul_role_scope_403", doGet("/mapping", as(CENTRE_A, "MEDECIN")));
        golden("autorisation_direction_role_scope_403", doGet("/mapping", as(CENTRE_A, "DIRECTION")));
        golden("autorisation_superadmin_role_scope_403", doGet("/mapping", as(CENTRE_A, "SUPERADMIN")));
        golden("autorisation_superadmin_ecriture_role_scope_403", doPut("/mapping", corps(), as(CENTRE_A, "SUPERADMIN")));
        golden("autorisation_admin_et_secretaire_lecture_200", doGet("/mapping", as(CENTRE_A, "ADMIN", "SECRETAIRE")));
        golden("autorisation_anonyme_lecture_403", doGetAnonymous("/mapping"));
        assertEquals(0, count("mapping_comptable", CENTRE_A));
    }

    @Test
    void le_mapping_n_a_ni_lecture_par_element_ni_creation_ni_suppression() throws Exception {
        golden("route_post_mapping_absente_500", doPost("/mapping", corps(), adminA()));
        golden("route_delete_mapping_absente_500", doDelete("/mapping", adminA()));
        golden("route_patch_mapping_absente_500", doRequest("PATCH", "/mapping", adminA()));
        golden("route_get_mapping_par_id_absente_500", doGet("/mapping/" + CENTRE_A, adminA()));
        golden("route_put_mapping_par_id_absente_500", doPut("/mapping/" + CENTRE_A, corps(), adminA()));
    }

    // ------------------------------------------------------------------ parcours

    @Test
    void parcours_complet_lire_les_defauts_personnaliser_relire_refuser_puis_conserver() throws Exception {
        golden("parcours_1_defauts_200", doGet("/mapping", adminA()));
        assertEquals(200, doPut("/comptes/7061", json("{'libelle':'Ventes de prestations bis','actif':true}"), adminA()).status());
        golden("parcours_2_personnalisation_200", doPut("/mapping", corps("compteVentes", "7061", "compteCaisse", "512"), adminA()));
        golden("parcours_3_relecture_200", doGet("/mapping", adminA()));
        golden("parcours_4_refus_compte_hors_plan_422", doPut("/mapping", corps("compteVentes", "70999"), adminA()));
        golden("parcours_5_relecture_inchangee_200", doGet("/mapping", adminA()));
        golden("parcours_6_compte_du_mapping_protege_422", doPut("/comptes/7061", json("{'libelle':'Ventes de prestations bis','actif':false}"), adminA()));
        goldenMapping("parcours_etat_base_final", CENTRE_A);
    }
}
