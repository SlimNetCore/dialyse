package com.hemodialyse.backend.characterization.comptabilite.referentiel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation C1 — journaux : {@code GET/PUT/DELETE /api/v1/comptabilite/journaux[/{code}]}.
 * Décrit le comportement ACTUEL, défauts compris (voir .ddd/safety-net/known-bugs.md, KB-CPT-01 et suivants).
 */
class JournauxCharacterizationIntegrationTest extends ReferentielCharacterizationSupport {

    private static final String MAPPING_PAR_DEFAUT = "{'compteVentes':'706','compteClientPatient':'411100',"
            + "'compteClientDefaut':'411500','compteBanque':'512','compteCaisse':'530','compteTVACollectee':'44571'";

    private static String corps(String libelle, boolean actif) {
        return json("{'libelle':'" + libelle + "','actif':" + actif + "}");
    }

    @Override
    protected String theme() {
        return "journaux";
    }

    // ------------------------------------------------------------------ journaux par défaut (RG-CPT-009)

    @Test
    void un_centre_sans_journal_voit_les_cinq_journaux_par_defaut_dans_l_ordre_de_declaration_sans_rien_enregistrer() throws Exception {
        Resp r = doGet("/journaux", adminA());
        assertEquals(200, r.status());
        assertEquals(5, json(r).get("total").asInt());
        golden("defaut_liste_cinq_journaux", r);
        assertEquals(0, count("journaux_comptables", CENTRE_A));
    }

    @Test
    void la_page_des_journaux_est_decoupee_en_memoire_avec_une_taille_bornee_a_100() throws Exception {
        golden("defaut_page_1_taille_2", doGet("/journaux?page=1&size=2", adminA()));
        golden("defaut_page_2_taille_2_derniere", doGet("/journaux?page=2&size=2", adminA()));
        golden("defaut_page_hors_limites", doGet("/journaux?page=9&size=2", adminA()));
        golden("defaut_taille_0_devient_1", doGet("/journaux?size=0", adminA()));
        golden("defaut_taille_negative_devient_1", doGet("/journaux?size=-5", adminA()));
        golden("defaut_page_negative_devient_0", doGet("/journaux?page=-1&size=2", adminA()));
        golden("defaut_taille_au_dela_de_100_ramenee_a_100", doGet("/journaux?size=100000", adminA()));
        golden("defaut_taille_illisible_500", doGet("/journaux?size=abc", adminA()));
        golden("defaut_parametres_de_recherche_ignores", doGet("/journaux?recherche=ban&actifs=true", adminA()));
    }

    @Test
    void la_premiere_modification_enregistre_les_journaux_par_defaut_puis_le_nouveau() throws Exception {
        Resp r = doPut("/journaux/OD", corps("Opérations diverses", true), adminA());
        assertEquals(200, r.status());
        golden("enregistrer_journal_nominal_200", r);
        assertEquals(6, count("journaux_comptables", CENTRE_A));
        goldenJournaux("enregistrer_journal_nominal_etat_base", CENTRE_A);
        golden("enregistrer_journal_nominal_liste_apres_tri_par_code", doGet("/journaux", adminA()));
    }

    @Test
    void modifier_un_journal_par_defaut_met_a_jour_sa_ligne_enregistree_sans_en_creer_une_autre() throws Exception {
        golden("modifier_journal_par_defaut_200", doPut("/journaux/VE", corps("Journal des ventes", true), adminA()));
        assertEquals(5, count("journaux_comptables", CENTRE_A));
        goldenJournaux("modifier_journal_par_defaut_etat_base", CENTRE_A);
    }

    @Test
    void enregistrer_deux_fois_le_meme_code_met_a_jour_au_lieu_de_dupliquer() throws Exception {
        assertEquals(200, doPut("/journaux/OD", corps("Opérations diverses", true), adminA()).status());
        golden("enregistrer_journal_existant_met_a_jour", doPut("/journaux/OD", corps("OD révisé", false), adminA()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM journaux_comptables WHERE center_id = ? AND code = 'OD'",
                Integer.class, CENTRE_A));
        goldenJournaux("enregistrer_journal_existant_etat_base", CENTRE_A);
    }

    // ------------------------------------------------------------------ formats et validations

    @Test
    void le_code_est_normalise_en_majuscules_rogne_et_limite_a_10_lettres_ou_chiffres() throws Exception {
        golden("code_minuscules_normalise_en_majuscules", doPut("/journaux/od", corps("Minuscules", true), adminA()));
        golden("code_mixte_normalise", doPut("/journaux/Op2", corps("Mixte", true), adminA()));
        golden("code_espaces_de_bord_rognes", doPut("/journaux/%20ab%20", corps("Espaces", true), adminA()));
        golden("code_numerique_accepte", doPut("/journaux/12", corps("Numérique", true), adminA()));
        golden("code_bornes_de_l_alphabet_et_des_chiffres_acceptees", doPut("/journaux/A0Z9", corps("Bornes", true), adminA()));
        golden("code_caractere_avant_A_refuse_400", doPut("/journaux/@", corps("Arobase", true), adminA()));
        golden("code_caractere_apres_Z_refuse_400", doPut("/journaux/%5B", corps("Crochet", true), adminA()));
        golden("code_caractere_apres_9_refuse_400", doPut("/journaux/:", corps("Deux points", true), adminA()));
        golden("code_10_caracteres_accepte", doPut("/journaux/ABCDEFGHIJ", corps("Dix", true), adminA()));
        golden("code_11_caracteres_refuse_400", doPut("/journaux/ABCDEFGHIJK", corps("Onze", true), adminA()));
        golden("code_tiret_refuse_400", doPut("/journaux/A-B", corps("Tiret", true), adminA()));
        golden("code_underscore_refuse_400", doPut("/journaux/A_B", corps("Underscore", true), adminA()));
        golden("code_accentue_refuse_400", doPut("/journaux/%C3%A9a", corps("Accent", true), adminA()));
        golden("code_espace_interne_refuse_400", doPut("/journaux/A%20B", corps("Espace", true), adminA()));
        golden("code_point_refuse_400", doPut("/journaux/A.B", corps("Point", true), adminA()));
        goldenJournaux("formats_code_etat_base", CENTRE_A);
    }

    @Test
    void le_libelle_est_obligatoire_rogne_et_limite_a_100_caracteres() throws Exception {
        golden("libelle_absent_400", doPut("/journaux/L1", json("{'actif':true}"), adminA()));
        golden("libelle_vide_400", doPut("/journaux/L1", corps("", true), adminA()));
        golden("libelle_blanc_400", doPut("/journaux/L1", corps("   ", true), adminA()));
        golden("libelle_rogne_a_l_enregistrement", doPut("/journaux/L2", corps("  Rogné  ", true), adminA()));
        golden("libelle_100_caracteres_accepte", doPut("/journaux/L3", corps("é".repeat(100), true), adminA()));
        golden("libelle_101_caracteres_refuse_400", doPut("/journaux/L4", corps("é".repeat(101), true), adminA()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM journaux_comptables WHERE center_id = ? AND code = 'L1'",
                Integer.class, CENTRE_A));
        goldenSql("libelle_etat_base", "SELECT code, LENGTH(libelle) AS longueur, libelle FROM journaux_comptables "
                + "WHERE center_id = '" + CENTRE_A + "' AND code IN ('L2','L3','L4') ORDER BY code");
    }

    @Test
    void un_journal_enregistre_sans_actif_est_refuse_en_500_comme_les_corps_illisibles() throws Exception {
        golden("actif_absent_400_nouveau_code", doPut("/journaux/J1", json("{'libelle':'Sans drapeau'}"), adminA()));
        golden("actif_texte_illisible_500", doPut("/journaux/J2", json("{'libelle':'Texte','actif':'oui'}"), adminA()));
        golden("corps_absent_500", doPutSansCorps("/journaux/J3", adminA()));
        golden("corps_json_illisible_500", doPut("/journaux/J3", "{pas du json", adminA()));
        golden("corps_objet_vide_400_nouveau_code", doPut("/journaux/J3", "{}", adminA()));
        golden("code_du_corps_ignore", doPut("/journaux/J4", json("{'code':'ZZ','libelle':'Code du corps','actif':true}"), adminA()));
        goldenJournaux("actif_etat_base", CENTRE_A);
    }

    // ------------------------------------------------------------------ désactivation et suppression (RG-CPT-009)

    @Test
    void les_cinq_journaux_par_defaut_sont_choisis_par_le_parametrage_donc_ni_desactives_ni_supprimes_et_le_refus_annule_l_enregistrement() throws Exception {
        for (String code : new String[]{"VE", "BQ", "CA", "AC", "ST"}) {
            assertEquals(422, doPut("/journaux/" + code, corps("x", false), adminA()).status(), code);
            assertEquals(422, doDelete("/journaux/" + code, adminA()).status(), code);
        }
        golden("desactiver_journal_du_mapping_422", doPut("/journaux/VE", corps("Ventes", false), adminA()));
        golden("supprimer_journal_du_mapping_422", doDelete("/journaux/BQ", adminA()));
        golden("supprimer_journal_du_mapping_en_minuscules_422", doDelete("/journaux/bq", adminA()));
        assertEquals(0, count("journaux_comptables", CENTRE_A));
    }

    @Test
    void un_journal_cesse_d_etre_protege_quand_le_mapping_ne_le_choisit_plus() throws Exception {
        assertEquals(200, doPut("/journaux/OD", corps("Opérations diverses", true), adminA()).status());
        // le mapping choisit OD pour les ventes : OD est protégé, VE ne l'est plus
        assertEquals(200, doPut("/mapping", json(MAPPING_PAR_DEFAUT + ",'journaux':{'VENTE':'OD'}}"), adminA()).status());
        golden("journal_choisi_pour_les_ventes_ne_se_desactive_pas", doPut("/journaux/OD", corps("Opérations diverses", false), adminA()));
        golden("journal_choisi_pour_les_ventes_ne_se_supprime_pas", doDelete("/journaux/OD", adminA()));
        golden("ancien_journal_des_ventes_se_desactive_200", doPut("/journaux/VE", corps("Ventes", false), adminA()));
        golden("ancien_journal_des_ventes_se_supprime_204", doDelete("/journaux/VE", adminA()));
        goldenJournaux("mapping_change_etat_base", CENTRE_A);
    }

    @Test
    void un_journal_choisi_pour_un_modele_de_piece_ne_se_desactive_ni_ne_se_supprime() throws Exception {
        assertEquals(200, doPut("/journaux/OD", corps("Opérations diverses", true), adminA()).status());
        String modele = json("{'code':'loyer','libelle':'Loyer','journal':'OD','actif':true,'lignes':["
                + "{'sens':'DEBIT','compte':'613'},{'sens':'CREDIT','compte':'401'}]}");
        assertEquals(201, doPost("/modeles", modele, adminA()).status());
        golden("desactiver_journal_d_un_modele_422", doPut("/journaux/OD", corps("Opérations diverses", false), adminA()));
        golden("supprimer_journal_d_un_modele_422", doDelete("/journaux/OD", adminA()));
        // la modification du libellé reste possible tant que le journal reste actif
        golden("renommer_journal_d_un_modele_200", doPut("/journaux/OD", corps("OD renommé", true), adminA()));
    }

    @Test
    void un_journal_qui_porte_des_ecritures_se_desactive_mais_ne_se_supprime_pas() throws Exception {
        journal(CENTRE_A, "XJ", "Journal avec écritures", true);
        ecriture(CENTRE_A, "XJ", "XJ-2026-000001", "512", "706");
        golden("supprimer_journal_avec_ecritures_422", doDelete("/journaux/XJ", adminA()));
        golden("desactiver_journal_avec_ecritures_200", doPut("/journaux/XJ", corps("Journal avec écritures", false), adminA()));
        golden("supprimer_journal_desactive_avec_ecritures_422", doDelete("/journaux/XJ", adminA()));
        goldenJournaux("ecritures_etat_base", CENTRE_A);
    }

    @Test
    void les_ecritures_d_un_autre_centre_ne_protegent_pas_le_journal() throws Exception {
        journal(CENTRE_A, "XJ", "Journal A", true);
        journal(CENTRE_B, "XJ", "Journal B", true);
        ecriture(CENTRE_B, "XJ", "XJ-2026-000001", "512", "706");
        golden("ecritures_autre_centre_suppression_204", doDelete("/journaux/XJ", adminA()));
        golden("ecritures_autre_centre_suppression_422_chez_b", doDelete("/journaux/XJ", adminB()));
    }

    @Test
    void un_journal_libre_se_supprime_physiquement_et_un_journal_inconnu_est_refuse_sans_rien_enregistrer() throws Exception {
        golden("supprimer_journal_inconnu_422_introuvable", doDelete("/journaux/ZZ", adminA()));
        assertEquals(0, count("journaux_comptables", CENTRE_A));
        assertEquals(200, doPut("/journaux/OD", corps("OD", true), adminA()).status());
        Resp ok = doDelete("/journaux/OD", adminA());
        assertEquals(204, ok.status());
        golden("supprimer_journal_libre_204", ok);
        assertEquals(5, count("journaux_comptables", CENTRE_A));
        golden("supprimer_journal_deja_supprime_422", doDelete("/journaux/OD", adminA()));
        golden("supprimer_code_trop_long_400", doDelete("/journaux/ABCDEFGHIJK", adminA()));
        golden("supprimer_code_mal_forme_400", doDelete("/journaux/A-B", adminA()));
        goldenJournaux("suppression_etat_base", CENTRE_A);
    }

    @Test
    void supprimer_le_dernier_journal_enregistre_ramene_les_journaux_par_defaut_a_la_lecture() throws Exception {
        journal(CENTRE_A, "XJ", "Seul journal du centre", true);
        golden("seul_journal_liste", doGet("/journaux", adminA()));
        assertEquals(204, doDelete("/journaux/XJ", adminA()).status());
        assertEquals(0, count("journaux_comptables", CENTRE_A));
        golden("seul_journal_supprime_les_defauts_reviennent", doGet("/journaux", adminA()));
    }

    // ------------------------------------------------------------------ journaux enregistrés : liste

    @Test
    void un_centre_qui_a_enregistre_ses_journaux_ne_voit_plus_les_journaux_par_defaut_triés_par_code() throws Exception {
        journal(CENTRE_A, "ZA", "Dernier", true);
        journal(CENTRE_A, "10", "Numérique", true);
        journal(CENTRE_A, "BQ", "Banque principale", false);
        journal(CENTRE_A, "AA", "Premier", true);
        golden("enregistres_liste_tri_par_code", doGet("/journaux", adminA()));
        golden("enregistres_page_2_taille_2", doGet("/journaux?page=1&size=2", adminA()));
    }

    // ------------------------------------------------------------------ isolation, autorisations

    @Test
    void chaque_centre_a_ses_journaux_et_le_centre_demande_doit_etre_celui_de_la_session() throws Exception {
        assertEquals(200, doPut("/journaux/OD", corps("OD du centre A", true), adminA()).status());
        golden("isolement_centre_b_voit_les_defauts", doGet("/journaux", adminB()));
        golden("isolement_centre_b_cree_le_meme_code", doPut("/journaux/OD", corps("OD du centre B", true), adminB()));
        golden("isolement_centre_a_inchange", doGet("/journaux?size=100", adminA()));
        golden("centre_autre_lecture_403", doGet("/journaux?centerId=" + CENTRE_B, adminA()));
        golden("centre_autre_enregistrement_403", doPut("/journaux/OX?centerId=" + CENTRE_B, corps("x", true), adminA()));
        golden("centre_autre_suppression_403", doDelete("/journaux/OD?centerId=" + CENTRE_B, adminA()));
        golden("centre_propre_explicite_accepte", doGet("/journaux?centerId=" + CENTRE_A + "&size=1", adminA()));
        goldenSql("isolement_etat_base", "SELECT center_id, code, libelle FROM journaux_comptables WHERE code = 'OD' "
                + "AND center_id IN ('" + CENTRE_A + "', '" + CENTRE_B + "') ORDER BY center_id");
    }

    @Test
    void la_consultation_est_reservee_a_admin_et_secretaire_et_les_ecritures_a_admin() throws Exception {
        golden("autorisation_secretaire_lecture_200", doGet("/journaux?size=1", secretaireA()));
        golden("autorisation_secretaire_enregistrement_403", doPut("/journaux/OD", corps("x", true), secretaireA()));
        golden("autorisation_secretaire_suppression_403", doDelete("/journaux/OD", secretaireA()));
        golden("autorisation_infirmier_lecture_403", doGet("/journaux", as(CENTRE_A, "INFIRMIER")));
        golden("autorisation_medecin_seul_lecture_403_role_scope", doGet("/journaux", as(CENTRE_A, "MEDECIN")));
        golden("autorisation_superadmin_lecture_403_role_scope", doGet("/journaux", as(CENTRE_A, "SUPERADMIN")));
        golden("autorisation_anonyme_lecture", doGetAnonymous("/journaux"));
        assertEquals(0, count("journaux_comptables", CENTRE_A));
    }

    @Test
    void les_methodes_et_routes_absentes_repondent_en_erreur_sans_rien_modifier() throws Exception {
        golden("route_post_journaux_absente", doRequest("POST", "/journaux", adminA()));
        golden("route_get_un_journal_absente", doGet("/journaux/VE", adminA()));
        golden("route_patch_journal_absente", doRequest("PATCH", "/journaux/VE", adminA()));
        golden("route_delete_collection_absente", doDelete("/journaux", adminA()));
        assertEquals(0, count("journaux_comptables", CENTRE_A));
    }

    // ------------------------------------------------------------------ parcours

    @Test
    void parcours_complet_creer_modifier_desactiver_lister_et_supprimer_un_journal() throws Exception {
        golden("parcours_1_creer", doPut("/journaux/OD", corps("Opérations diverses", true), adminA()));
        golden("parcours_2_renommer", doPut("/journaux/od", corps("Écritures diverses", true), adminA()));
        golden("parcours_3_desactiver", doPut("/journaux/OD", corps("Écritures diverses", false), adminA()));
        golden("parcours_4_lister", doGet("/journaux?size=100", adminA()));
        golden("parcours_5_supprimer", doDelete("/journaux/OD", adminA()));
        golden("parcours_6_lister_apres_suppression", doGet("/journaux?size=100", adminA()));
        goldenJournaux("parcours_etat_final_base", CENTRE_A);
    }
}
