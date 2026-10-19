package com.hemodialyse.backend.characterization.comptabilite.mappingtva;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation C2 — règles de TVA comptable : {@code GET /regles-tva} et {@code POST /regles-tva} (table
 * {@code tva_types}, partagée avec la Facturation). Point d'observation : HTTP (MockMvc, Spring complet, sécurité
 * réelle + RoleScopeFilter, H2) et état SQL de {@code tva_types}.
 */
class ReglesTvaCharacterizationIntegrationTest extends MappingTvaCharacterizationSupport {

    private static String regle(String type, String taux, String exonere, String debut, String fin, String texte) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("'typePrestation':").append(type == null ? "null" : "'" + type + "'");
        sb.append(",'tauxApplique':").append(taux);
        if (exonere != null) sb.append(",'exonere':").append(exonere);
        sb.append(",'dateDebutValidite':").append(debut == null ? "null" : "'" + debut + "'");
        if (fin != null) sb.append(",'dateFinValidite':").append(fin.equals("null") ? "null" : "'" + fin + "'");
        if (texte != null) sb.append(",'texteReference':'").append(texte).append("'");
        return json(sb.append("}").toString());
    }

    private static String nominale() {
        return regle("HEMODIALYSE", "19.00", "false", "2026-01-01", null, null);
    }

    @Override
    protected String theme() {
        return "regles-tva";
    }

    // ------------------------------------------------------------------ lecture

    @Test
    void un_centre_sans_regle_lit_une_liste_vide() throws Exception {
        golden("lecture_aucune_regle_200", doGet("/regles-tva", adminA()));
    }

    @Test
    void les_regles_sont_listees_de_la_plus_recente_a_la_plus_ancienne_toutes_prestations_et_etats_confondus() throws Exception {
        regle(CENTRE_A, "HEMODIALYSE", "19.00", false, "2024-01-01", "2025-12-31", true);
        regle(CENTRE_A, "HEMODIALYSE", "9.00", false, "2026-01-01", null, true);
        regle(CENTRE_A, "MEDICAMENT", "0.00", true, "2025-06-01", null, true);
        regle(CENTRE_A, "HEMODIALYSE", "7.00", false, "2023-01-01", "2023-12-31", false);
        regle(CENTRE_B, "HEMODIALYSE", "19.00", false, "2026-01-01", null, true);
        golden("lecture_liste_triee_par_debut_decroissant_200", doGet("/regles-tva", adminA()));
        golden("lecture_autre_centre_ne_voit_que_les_siennes_200", doGet("/regles-tva", adminB()));
    }

    @Test
    void la_liste_n_est_ni_paginee_ni_filtree() throws Exception {
        for (int i = 1; i <= 25; i++) {
            regle(CENTRE_A, "HEMODIALYSE", "19.00", false, String.format("2020-01-%02d", i), null, true);
        }
        Resp r = doGet("/regles-tva?page=1&size=5&actif=true&typePrestation=AUTRE", adminA());
        assertEquals(200, r.status());
        assertEquals(25, json(r).size());
        assertEquals("2020-01-25", json(r).get(0).get("dateDebutValidite").asString());
        assertEquals("2020-01-01", json(r).get(24).get("dateDebutValidite").asString());
    }

    @Test
    void une_regle_sans_date_de_fin_se_lit_avec_une_date_de_fin_vide() throws Exception {
        regle(CENTRE_A, "HEMODIALYSE", "19.00", false, "2026-01-01", null, true);
        regle(CENTRE_A, "AUTRE", "5.50", false, "2025-01-01", "2025-06-30", true);
        golden("lecture_date_de_fin_vide_ou_renseignee_200", doGet("/regles-tva", adminA()));
    }

    @Test
    void le_texte_de_reference_et_le_libelle_ne_sont_pas_exposes_par_la_lecture() throws Exception {
        assertEquals(200, doPost("/regles-tva", regle("HEMODIALYSE", "19.00", "false", "2026-01-01", null,
                "Loi de finances 2026"), adminA()).status());
        Resp r = doGet("/regles-tva", adminA());
        assertEquals(java.util.List.of("dateDebutValidite", "dateFinValidite", "exonere", "tauxApplique", "typePrestation"),
                json(r).get(0).propertyNames().stream().sorted().toList());
    }

    // ------------------------------------------------------------------ enregistrement nominal

    @Test
    void l_enregistrement_ajoute_une_regle_active_et_repond_saved_true_en_200() throws Exception {
        golden("enregistrement_nominal_200", doPost("/regles-tva", nominale(), adminA()));
        goldenRegles("enregistrement_nominal_etat_base", CENTRE_A);
        golden("enregistrement_nominal_relue_200", doGet("/regles-tva", adminA()));
    }

    @Test
    void l_enregistrement_complet_porte_la_date_de_fin_le_texte_de_reference_et_l_exoneration() throws Exception {
        golden("enregistrement_complet_200", doPost("/regles-tva", regle("HEMODIALYSE", "9.00", "true", "2026-01-01",
                "2026-12-31", "Article 13 du code de la TVA"), adminA()));
        goldenRegles("enregistrement_complet_etat_base", CENTRE_A);
    }

    @Test
    void une_exoneration_conserve_le_taux_saisi_et_l_exoneration_absente_est_une_erreur_interne_500() throws Exception {
        golden("exonere_absent_500", doPost("/regles-tva", regle("HEMODIALYSE", "5", null, "2026-01-01", null, null), adminA()));
        assertEquals(0, count("tva_types", CENTRE_A));
        assertEquals(200, doPost("/regles-tva", regle("HEMODIALYSE", "19.00", "true", "2026-01-01", null, null), adminA()).status());
        assertEquals(200, doPost("/regles-tva", regle("AUTRE", "5", "false", "2026-01-01", null, null), adminA()).status());
        goldenRegles("exoneration_et_defaut_etat_base", CENTRE_A);
    }

    @Test
    void chaque_enregistrement_ajoute_une_ligne_sans_jamais_modifier_ni_fermer_les_precedentes() throws Exception {
        // écart décidé (KB-CPT-16) : une règle identique ou qui chevauche l'existante est refusée ; la règle successeuse d'une règle fermée est acceptée
        assertEquals(200, doPost("/regles-tva", regle("HEMODIALYSE", "19.00", "false", "2026-01-01", "2026-06-30", null), adminA()).status());
        assertEquals(400, doPost("/regles-tva", regle("HEMODIALYSE", "19.00", "false", "2026-01-01", "2026-06-30", null), adminA()).status());
        assertEquals(200, doPost("/regles-tva", regle("HEMODIALYSE", "9.00", "false", "2026-07-01", null, null), adminA()).status());
        assertEquals(2, count("tva_types", CENTRE_A));
        goldenRegles("ajouts_successifs_sans_fermeture_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void le_libelle_de_la_ligne_est_compose_du_type_et_du_taux_saisi() throws Exception {
        assertEquals(200, doPost("/regles-tva", regle("HEMODIALYSE", "19", "false", "2026-01-01", "2026-01-31", null), adminA()).status());
        assertEquals(200, doPost("/regles-tva", regle("HEMODIALYSE", "19.0", "false", "2026-02-01", "2026-02-28", null), adminA()).status());
        assertEquals(200, doPost("/regles-tva", regle("HEMODIALYSE", "19.50", "false", "2026-03-01", "2026-03-31", null), adminA()).status());
        goldenRegles("libelle_compose_etat_base_nouveau_code", CENTRE_A);
    }

    // ------------------------------------------------------------------ taux

    @Test
    void le_taux_est_borne_par_zero_en_validation_et_par_la_precision_de_la_colonne() throws Exception {
        // les règles reçoivent une fin de mois : deux règles ouvertes du même type se chevaucheraient (KB-CPT-16, écart décidé)
        golden("taux_zero_200", doPost("/regles-tva", regle("HEMODIALYSE", "0", "false", "2026-01-01", "2026-01-31", null), adminA()));
        golden("taux_zero_virgule_zero_zero_200", doPost("/regles-tva", regle("HEMODIALYSE", "0.00", "false", "2026-02-01", "2026-02-28", null), adminA()));
        golden("taux_negatif_400", doPost("/regles-tva", regle("HEMODIALYSE", "-0.01", "false", "2026-01-01", null, null), adminA()));
        golden("taux_moins_un_400", doPost("/regles-tva", regle("HEMODIALYSE", "-1", "false", "2026-01-01", null, null), adminA()));
        golden("taux_cent_200", doPost("/regles-tva", regle("HEMODIALYSE", "100", "false", "2026-03-01", "2026-03-31", null), adminA()));
        golden("taux_au_dessus_de_cent_400_nouveau_code", doPost("/regles-tva", regle("HEMODIALYSE", "150", "false", "2026-04-01", "2026-04-30", null), adminA()));
        golden("taux_999_99_400_nouveau_code", doPost("/regles-tva", regle("HEMODIALYSE", "999.99", "false", "2026-05-01", "2026-05-31", null), adminA()));
        golden("taux_1000_400_nouveau_code", doPost("/regles-tva", regle("HEMODIALYSE", "1000", "false", "2026-06-01", null, null), adminA()));
        goldenRegles("taux_bornes_0_a_100_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void le_taux_absent_nul_ou_illisible_est_refuse() throws Exception {
        golden("taux_absent_400", doPost("/regles-tva", json("{'typePrestation':'HEMODIALYSE','exonere':false,'dateDebutValidite':'2026-01-01'}"), adminA()));
        golden("taux_nul_400", doPost("/regles-tva", regle("HEMODIALYSE", "null", "false", "2026-01-01", null, null), adminA()));
        golden("taux_texte_illisible_500", doPost("/regles-tva", regle("HEMODIALYSE", "'abc'", "false", "2026-01-01", null, null), adminA()));
        golden("taux_texte_numerique_coerce_200", doPost("/regles-tva", regle("HEMODIALYSE", "'19'", "false", "2026-01-01", null, null), adminA()));
        assertEquals(1, count("tva_types", CENTRE_A));
    }

    @Test
    void un_taux_a_plus_de_deux_decimales_est_traite_par_la_colonne() throws Exception {
        golden("taux_trois_decimales_arrondi_ou_refus", doPost("/regles-tva", regle("HEMODIALYSE", "19.999", "false", "2026-01-01", "2026-01-31", null), adminA()));
        golden("taux_trois_decimales_demi_arrondi_ou_refus", doPost("/regles-tva", regle("HEMODIALYSE", "19.005", "false", "2026-02-01", "2026-02-28", null), adminA()));
        golden("taux_un_dixieme_200", doPost("/regles-tva", regle("HEMODIALYSE", "5.5", "false", "2026-03-01", "2026-03-31", null), adminA()));
        goldenRegles("taux_decimales_etat_base_nouveau_code", CENTRE_A);
    }

    // ------------------------------------------------------------------ type de prestation

    @Test
    void le_type_de_prestation_est_obligatoire_mais_n_est_ni_rogne_ni_normalise_ni_limite_par_la_validation() throws Exception {
        golden("type_absent_400", doPost("/regles-tva", json("{'tauxApplique':19,'exonere':false,'dateDebutValidite':'2026-01-01'}"), adminA()));
        golden("type_nul_400", doPost("/regles-tva", regle(null, "19", "false", "2026-01-01", null, null), adminA()));
        golden("type_vide_400", doPost("/regles-tva", regle("", "19", "false", "2026-01-01", null, null), adminA()));
        golden("type_blanc_400", doPost("/regles-tva", regle("   ", "19", "false", "2026-01-01", null, null), adminA()));
        golden("type_avec_espaces_conserve_tel_quel_200", doPost("/regles-tva", regle(" HEMODIALYSE ", "19", "false", "2026-01-01", null, null), adminA()));
        golden("type_en_minuscules_conserve_tel_quel_200", doPost("/regles-tva", regle("hemodialyse", "19", "false", "2026-02-01", null, null), adminA()));
        golden("type_avec_accents_200", doPost("/regles-tva", regle("Médicament à 5%", "5", "false", "2026-03-01", null, null), adminA()));
        goldenRegles("type_etat_base", CENTRE_A);
    }

    @Test
    void le_type_de_prestation_est_limite_a_120_caracteres_par_la_colonne() throws Exception {
        golden("type_120_caracteres_200", doPost("/regles-tva", regle("T".repeat(120), "19", "false", "2026-01-01", null, null), adminA()));
        golden("type_121_caracteres_depasse_la_colonne", doPost("/regles-tva", regle("T".repeat(121), "19", "false", "2026-02-01", null, null), adminA()));
        assertEquals(1, count("tva_types", CENTRE_A));
    }

    @Test
    void le_texte_de_reference_est_limite_a_500_caracteres_par_la_colonne() throws Exception {
        golden("texte_500_caracteres_200", doPost("/regles-tva", regle("HEMODIALYSE", "19", "false", "2026-01-01", "2026-01-31", "x".repeat(500)), adminA()));
        golden("texte_501_caracteres_depasse_la_colonne", doPost("/regles-tva", regle("HEMODIALYSE", "19", "false", "2026-02-01", null, "x".repeat(501)), adminA()));
        assertEquals(1, count("tva_types", CENTRE_A));
    }

    // ------------------------------------------------------------------ dates d'effet

    @Test
    void la_date_de_debut_est_obligatoire_et_la_date_de_fin_ne_peut_preceder_le_debut() throws Exception {
        golden("debut_absent_400", doPost("/regles-tva", json("{'typePrestation':'HEMODIALYSE','tauxApplique':19,'exonere':false}"), adminA()));
        golden("debut_nul_400", doPost("/regles-tva", regle("HEMODIALYSE", "19", "false", null, null, null), adminA()));
        // écart décidé (KB-CPT-16) : une fin antérieure au début est refusée (400) ; une fin égale au début reste acceptée
        golden("fin_avant_debut_refusee_400_nouveau_code", doPost("/regles-tva", regle("HEMODIALYSE", "19", "false", "2026-06-01", "2026-01-01", null), adminA()));
        golden("fin_veille_du_debut_refusee_400_nouveau_code", doPost("/regles-tva", regle("HEMODIALYSE", "19", "false", "2026-06-01", "2026-05-31", null), adminA()));
        assertEquals(0, count("tva_types", CENTRE_A));
        golden("fin_egale_debut_acceptee_200", doPost("/regles-tva", regle("HEMODIALYSE", "19", "false", "2026-07-01", "2026-07-01", null), adminA()));
        golden("fin_nulle_explicite_200", doPost("/regles-tva", regle("HEMODIALYSE", "19", "false", "2026-08-01", "null", null), adminA()));
        // un autre type : les dates extrêmes couvriraient les règles précédentes du même type
        golden("dates_extremes_200", doPost("/regles-tva", regle("EXTREMES", "19", "false", "0001-01-01", "9999-12-31", null), adminA()));
        goldenRegles("dates_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void les_dates_mal_formees_sont_une_erreur_interne_500() throws Exception {
        golden("debut_mois_13_500", doPost("/regles-tva", regle("HEMODIALYSE", "19", "false", "2026-13-45", null, null), adminA()));
        golden("debut_format_francais_500", doPost("/regles-tva", regle("HEMODIALYSE", "19", "false", "01/01/2026", null, null), adminA()));
        golden("fin_texte_500", doPost("/regles-tva", regle("HEMODIALYSE", "19", "false", "2026-01-01", "demain", null), adminA()));
        golden("debut_29_fevrier_annee_non_bissextile_500", doPost("/regles-tva", regle("HEMODIALYSE", "19", "false", "2026-02-29", null, null), adminA()));
        assertEquals(0, count("tva_types", CENTRE_A));
    }

    @Test
    void des_regles_qui_se_chevauchent_sont_refusees_en_400() throws Exception {
        // écart décidé (KB-CPT-16) : intervalles [début, fin] inclusifs, fin absente = ouvert, même centre et même type
        golden("chevauchement_1_200", doPost("/regles-tva", regle("HEMODIALYSE", "19", "false", "2026-01-01", "2026-12-31", null), adminA()));
        golden("chevauchement_2_400_nouveau_code", doPost("/regles-tva", regle("HEMODIALYSE", "9", "false", "2026-06-01", "2027-06-30", null), adminA()));
        golden("chevauchement_3_meme_periode_400_nouveau_code", doPost("/regles-tva", regle("HEMODIALYSE", "19", "false", "2026-01-01", "2026-12-31", null), adminA()));
        assertEquals(1, count("tva_types", CENTRE_A));
    }

    @Test
    void les_bornes_du_chevauchement_sont_inclusives_et_une_regle_ouverte_chevauche_toute_regle_posterieure() throws Exception {
        assertEquals(200, doPost("/regles-tva", regle("HEMODIALYSE", "19", "false", "2026-01-01", "2026-06-30", null), adminA()).status());
        golden("chevauchement_borne_commune_400_nouveau_code", doPost("/regles-tva", regle("HEMODIALYSE", "9", "false", "2026-06-30", null, null), adminA()));
        golden("chevauchement_lendemain_de_la_fin_200_nouveau_code", doPost("/regles-tva", regle("HEMODIALYSE", "9", "false", "2026-07-01", null, null), adminA()));
        golden("chevauchement_regle_ouverte_posterieure_400_nouveau_code", doPost("/regles-tva", regle("HEMODIALYSE", "5", "false", "2030-01-01", null, null), adminA()));
        golden("chevauchement_regle_anterieure_qui_deborde_400_nouveau_code", doPost("/regles-tva", regle("HEMODIALYSE", "5", "false", "2025-01-01", "2026-01-01", null), adminA()));
        golden("chevauchement_regle_anterieure_close_200_nouveau_code", doPost("/regles-tva", regle("HEMODIALYSE", "5", "false", "2025-01-01", "2025-12-31", null), adminA()));
        goldenRegles("chevauchement_bornes_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void le_chevauchement_ne_compte_ni_les_autres_types_ni_les_autres_centres_ni_les_regles_desactivees() throws Exception {
        regle(CENTRE_A, "HEMODIALYSE", "19.00", false, "2026-01-01", null, false);
        regle(CENTRE_B, "HEMODIALYSE", "19.00", false, "2026-01-01", null, true);
        regle(CENTRE_A, "MEDICAMENT", "5.00", false, "2026-01-01", null, true);
        golden("chevauchement_ignore_desactivee_autre_centre_autre_type_200_nouveau_code",
                doPost("/regles-tva", regle("HEMODIALYSE", "9", "false", "2026-03-01", null, null), adminA()));
        golden("chevauchement_type_compare_tel_que_saisi_200_nouveau_code",
                doPost("/regles-tva", regle("hemodialyse", "9", "false", "2026-03-01", null, null), adminA()));
        golden("chevauchement_meme_regle_refusee_400_nouveau_code",
                doPost("/regles-tva", regle("HEMODIALYSE", "9", "false", "2026-03-01", null, null), adminA()));
        assertEquals(4, count("tva_types", CENTRE_A));
        assertEquals(1, count("tva_types", CENTRE_B));
    }

    @Test
    void une_regle_anterieure_deja_en_base_qui_se_chevauche_n_empeche_ni_la_lecture_ni_un_ajout_hors_de_ses_dates() throws Exception {
        regle(CENTRE_A, "HEMODIALYSE", "19.00", false, "2026-01-01", null, true);
        regle(CENTRE_A, "HEMODIALYSE", "9.00", false, "2026-01-01", null, true);
        golden("chevauchement_regles_deja_en_base_lues_200_nouveau_code", doGet("/regles-tva", adminA()));
        golden("chevauchement_ajout_dans_les_dates_d_une_regle_en_base_400_nouveau_code",
                doPost("/regles-tva", regle("HEMODIALYSE", "5", "false", "2027-01-01", null, null), adminA()));
        golden("chevauchement_ajout_avant_les_regles_en_base_200_nouveau_code",
                doPost("/regles-tva", regle("HEMODIALYSE", "5", "false", "2024-01-01", "2025-12-31", null), adminA()));
    }

    @Test
    void l_exonere_non_booleen_est_une_erreur_interne_et_exonere_nul_est_refuse() throws Exception {
        golden("exonere_texte_oui_500", doPost("/regles-tva", regle("HEMODIALYSE", "19", "'oui'", "2026-01-01", null, null), adminA()));
        golden("exonere_nul_500", doPost("/regles-tva", regle("HEMODIALYSE", "19", "null", "2026-01-01", null, null), adminA()));
        golden("exonere_texte_true_coerce_200", doPost("/regles-tva", regle("HEMODIALYSE", "19", "'true'", "2026-02-01", "2026-02-28", null), adminA()));
        golden("exonere_nombre_un_coerce_200", doPost("/regles-tva", regle("HEMODIALYSE", "19", "1", "2026-03-01", "2026-03-31", null), adminA()));
        goldenRegles("exonere_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void les_proprietes_inconnues_dont_un_centre_et_un_identifiant_sont_ignorees() throws Exception {
        golden("propriete_inconnue_ignoree_200", doPost("/regles-tva", nominale().replaceFirst("}$",
                json(",'centerId':'" + CENTRE_B + "','id':'abc','actif':false}")), adminA()));
        assertEquals(1, count("tva_types", CENTRE_A));
        assertEquals(0, count("tva_types", CENTRE_B));
        goldenRegles("propriete_inconnue_etat_base", CENTRE_A);
    }

    @Test
    void les_corps_illisibles_sont_une_erreur_interne_500() throws Exception {
        golden("corps_absent_500", doPostSansCorps("/regles-tva", adminA()));
        golden("corps_illisible_500", doPost("/regles-tva", "{pas du json", adminA()));
        golden("corps_tableau_500", doPost("/regles-tva", "[]", adminA()));
        golden("corps_nul_500", doPost("/regles-tva", "null", adminA()));
        golden("corps_texte_500", doPostTexte("/regles-tva", nominale(), adminA()));
        golden("corps_objet_vide_500", doPost("/regles-tva", "{}", adminA()));
        assertEquals(0, count("tva_types", CENTRE_A));
    }

    @Test
    void les_erreurs_de_validation_multiples_sont_toutes_signalees_en_un_message_generique() throws Exception {
        golden("plusieurs_erreurs_de_validation_400", doPost("/regles-tva", regle("", "-5", "false", null, null, null), adminA()));
    }

    // ------------------------------------------------------------------ centres, autorisations, routes

    @Test
    void le_centre_est_celui_de_la_session_et_un_autre_centre_demande_est_refuse() throws Exception {
        golden("enregistrement_centre_d_un_autre_403", doPost("/regles-tva?centerId=" + CENTRE_B, nominale(), adminA()));
        golden("enregistrement_centre_propre_200", doPost("/regles-tva?centerId=" + CENTRE_A, nominale(), adminA()));
        golden("enregistrement_centre_illisible_500", doPost("/regles-tva?centerId=pas-un-uuid", nominale(), adminA()));
        golden("lecture_centre_d_un_autre_403", doGet("/regles-tva?centerId=" + CENTRE_B, adminA()));
        golden("lecture_centre_propre_200", doGet("/regles-tva?centerId=" + CENTRE_A, adminA()));
        golden("lecture_centre_illisible_500", doGet("/regles-tva?centerId=pas-un-uuid", adminA()));
        assertEquals(0, count("tva_types", CENTRE_B));
        assertEquals(1, count("tva_types", CENTRE_A));
    }

    @Test
    void un_centre_ne_voit_ni_ne_complete_les_regles_d_un_autre_centre() throws Exception {
        assertEquals(200, doPost("/regles-tva", nominale(), adminA()).status());
        assertEquals(200, doPost("/regles-tva", regle("HEMODIALYSE", "9", "false", "2026-05-01", null, null), adminB()).status());
        golden("isolation_centre_a_200", doGet("/regles-tva", adminA()));
        golden("isolation_centre_b_200", doGet("/regles-tva", adminB()));
        golden("isolation_centre_vide_200", doGet("/regles-tva", as(CENTRE_VIDE, "ADMIN")));
    }

    @Test
    void seul_l_administrateur_lit_et_enregistre_les_regles_de_tva() throws Exception {
        golden("autorisation_secretaire_lecture_403", doGet("/regles-tva", as(CENTRE_A, "SECRETAIRE")));
        golden("autorisation_secretaire_ecriture_403", doPost("/regles-tva", nominale(), as(CENTRE_A, "SECRETAIRE")));
        golden("autorisation_infirmier_lecture_403", doGet("/regles-tva", as(CENTRE_A, "INFIRMIER")));
        golden("autorisation_infirmier_ecriture_403", doPost("/regles-tva", nominale(), as(CENTRE_A, "INFIRMIER")));
        golden("autorisation_medecin_seul_role_scope_403", doGet("/regles-tva", as(CENTRE_A, "MEDECIN")));
        golden("autorisation_direction_role_scope_403", doGet("/regles-tva", as(CENTRE_A, "DIRECTION")));
        golden("autorisation_superadmin_role_scope_403", doGet("/regles-tva", as(CENTRE_A, "SUPERADMIN")));
        golden("autorisation_superadmin_ecriture_role_scope_403", doPost("/regles-tva", nominale(), as(CENTRE_A, "SUPERADMIN")));
        golden("autorisation_anonyme_lecture_403", doGetAnonymous("/regles-tva"));
        assertEquals(0, count("tva_types", CENTRE_A));
    }

    @Test
    void les_regles_n_ont_ni_modification_ni_suppression_ni_lecture_par_element() throws Exception {
        regle(CENTRE_A, "HEMODIALYSE", "19.00", false, "2026-01-01", null, true);
        golden("route_put_regles_absente_500", doPut("/regles-tva", nominale(), adminA()));
        golden("route_delete_regles_absente_500", doDelete("/regles-tva", adminA()));
        golden("route_patch_regles_absente_500", doRequest("PATCH", "/regles-tva", adminA()));
        golden("route_get_regle_par_id_absente_500", doGet("/regles-tva/" + CENTRE_A, adminA()));
        golden("route_delete_regle_par_id_absente_500", doDelete("/regles-tva/" + CENTRE_A, adminA()));
        golden("route_put_regle_par_id_absente_500", doPut("/regles-tva/" + CENTRE_A, nominale(), adminA()));
        assertEquals(1, count("tva_types", CENTRE_A));
    }

    // ------------------------------------------------------------------ parcours

    @Test
    void parcours_complet_lire_vide_ajouter_une_regle_puis_sa_successeuse_et_relire() throws Exception {
        golden("parcours_1_vide_200", doGet("/regles-tva", adminA()));
        // la règle d'origine est fermée avant sa successeuse : une règle ouverte et une règle postérieure se chevaucheraient (KB-CPT-16)
        golden("parcours_2_premiere_regle_200", doPost("/regles-tva", regle("HEMODIALYSE", "19", "false", "2024-01-01", "2025-12-31",
                "Taux normal"), adminA()));
        golden("parcours_3_regle_successeuse_200", doPost("/regles-tva", regle("HEMODIALYSE", "9", "false", "2026-01-01", null,
                "Taux réduit"), adminA()));
        golden("parcours_4_relecture_200_nouveau_code", doGet("/regles-tva", adminA()));
        golden("parcours_5_regle_refusee_400", doPost("/regles-tva", regle("HEMODIALYSE", "-9", "false", "2026-01-01", null, null), adminA()));
        golden("parcours_5b_regle_chevauchante_refusee_400_nouveau_code", doPost("/regles-tva", regle("HEMODIALYSE", "5", "false", "2025-06-01", "2026-06-30", null), adminA()));
        golden("parcours_6_relecture_inchangee_200_nouveau_code", doGet("/regles-tva", adminA()));
        goldenRegles("parcours_etat_base_final_nouveau_code", CENTRE_A);
    }
}
