package com.hemodialyse.backend.characterization.planification.parametrage;

import com.hemodialyse.backend.characterization.planification.PlanificationCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation P1 : paramètres du planning du centre, {@code GET|PUT /api/v1/planning/parametres}
 * (RG-PLN-001 à 006). Points d'observation : HTTP (réponse, statut, Cache-Control) et état SQL de
 * {@code planning_parametres} et de {@code salle.isolement}.
 */
class ParametresDuCentreCharacterizationIntegrationTest extends PlanificationCharacterizationSupport {

    private static final String URL = PLANNING + "/parametres";

    @Override
    protected String theme() {
        return "p1-parametrage/parametres";
    }

    private void etatBase(String name) {
        goldenSql(name + "_planning_parametres", "SELECT center_id, jours_ouverts, salles_isolement, patients_par_infirmier, "
                + "patients_par_poste_serie, (updated_at IS NULL) AS updated_at_nul FROM planning_parametres "
                + "WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY center_id");
        goldenSql(name + "_salles", "SELECT center_id, code, isolement FROM salle WHERE center_id IN ('" + CENTRE_A + "','"
                + CENTRE_B + "') ORDER BY center_id, code");
    }

    private int lignes() {
        return count("planning_parametres");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE center_id IN (?, ?)", Integer.class, CENTRE_A, CENTRE_B);
    }

    // ------------------------------------------------------------------ lecture

    @Test
    void un_centre_sans_parametrage_dialyse_tous_les_jours_avec_les_valeurs_par_defaut_et_rien_n_est_cree() throws Exception {
        golden("lecture_sans_ligne_defauts_200", doGet(URL, adminA()));
        assertEquals(0, lignes());
    }

    @Test
    void les_salles_d_isolement_lues_sont_celles_de_la_table_salle_triees_par_identifiant() throws Exception {
        salle(CENTRE_A, "SALLE_1", "Salle 1", "NON");
        salle(CENTRE_A, "SALLE_ISO_1", "Isolement 1", "OUI");
        salle(CENTRE_A, "SALLE_ISO_2", "Isolement 2", "OUI");
        salle(CENTRE_B, "SALLE_B_ISO", "Isolement du centre B", "OUI");
        golden("lecture_salles_isolement_de_la_table_salle_200", doGet(URL, adminA()));
        golden("lecture_centre_B_ne_voit_que_ses_salles_200", doGet(URL, adminB()));
        assertEquals(0, lignes());
    }

    @Test
    void une_ligne_ancienne_aux_colonnes_vides_est_lue_avec_les_valeurs_par_defaut() throws Exception {
        jdbc.update("INSERT INTO planning_parametres (center_id, jours_ouverts, patients_par_infirmier, "
                + "patients_par_poste_serie, updated_at) VALUES (?, 'LUNDI,MARDI', NULL, NULL, CURRENT_TIMESTAMP)", CENTRE_A);
        golden("lecture_ligne_ancienne_ratio_et_poste_nuls_200", doGet(URL, adminA()));
        jdbc.update("UPDATE planning_parametres SET jours_ouverts = '' WHERE center_id = ?", CENTRE_A);
        golden("lecture_ligne_sans_jours_donne_tous_les_jours_200", doGet(URL, adminA()));
    }

    // ------------------------------------------------------------------ écriture nominale

    @Test
    void l_enregistrement_cree_la_ligne_du_centre_et_remplace_les_salles_d_isolement() throws Exception {
        salle(CENTRE_A, "SALLE_1", "Salle 1", "OUI");
        UUID s2 = salle(CENTRE_A, "SALLE_2", "Salle 2", "NON");
        UUID s3 = salle(CENTRE_A, "SALLE_3", "Salle 3", "NON");
        salle(CENTRE_B, "SALLE_B", "Salle du centre B", "OUI");
        goldenSimple("ecriture_nominale_200", doPut(URL, json("{'joursOuverts':['SAMEDI','LUNDI','MERCREDI','LUNDI'],"
                + "'sallesIsolement':['" + s3 + "','" + s2 + "'],'patientsParInfirmier':6,'patientsParPosteEtSerie':2}"), adminA()));
        etatBase("ecriture_nominale_etat_base");
        golden("ecriture_nominale_relecture_200", doGet(URL, adminA()));
    }

    @Test
    void un_enregistrement_sans_salle_efface_toutes_les_salles_d_isolement_du_centre_seulement() throws Exception {
        salle(CENTRE_A, "SALLE_ISO_1", "Isolement 1", "OUI");
        salle(CENTRE_A, "SALLE_ISO_2", "Isolement 2", "OUI");
        salle(CENTRE_B, "SALLE_B_ISO", "Isolement B", "OUI");
        goldenSimple("ecriture_sans_salle_200", doPut(URL, json("{'joursOuverts':['LUNDI']}"), adminA()));
        etatBase("ecriture_sans_salle_etat_base");
        goldenSimple("ecriture_salles_nulles_200", doPut(URL, json("{'joursOuverts':['LUNDI'],'sallesIsolement':null}"), adminA()));
    }

    @Test
    void les_valeurs_absentes_prennent_les_defauts_ratio_4_et_3_patients_par_poste() throws Exception {
        goldenSimple("ecriture_ratio_et_poste_absents_200", doPut(URL, json("{'joursOuverts':['DIMANCHE']}"), adminA()));
        goldenSimple("ecriture_ratio_et_poste_nuls_200",
                doPut(URL, json("{'joursOuverts':['DIMANCHE'],'patientsParInfirmier':null,'patientsParPosteEtSerie':null}"), adminA()));
        goldenSimple("ecriture_ratio_seul_garde_le_poste_par_defaut_200",
                doPut(URL, json("{'joursOuverts':['DIMANCHE'],'patientsParInfirmier':9}"), adminA()));
        goldenSimple("ecriture_poste_seul_remet_le_ratio_par_defaut_200",
                doPut(URL, json("{'joursOuverts':['DIMANCHE'],'patientsParPosteEtSerie':7}"), adminA()));
        etatBase("ecriture_defauts_etat_base");
    }

    @Test
    void l_enregistrement_est_idempotent_et_la_derniere_ecriture_l_emporte() throws Exception {
        String corps = "{'joursOuverts':['LUNDI','JEUDI'],'patientsParInfirmier':5,'patientsParPosteEtSerie':4}";
        goldenSimple("idempotence_premier_appel_200", doPut(URL, json(corps), adminA()));
        goldenSimple("idempotence_second_appel_identique_200", doPut(URL, json(corps), adminA()));
        assertEquals(1, lignes());
        goldenSimple("derniere_ecriture_l_emporte_200",
                doPut(URL, json("{'joursOuverts':['MARDI'],'patientsParInfirmier':8,'patientsParPosteEtSerie':1}"), adminA()));
        etatBase("derniere_ecriture_etat_base");
        assertEquals(1, lignes());
    }

    @Test
    void chaque_centre_a_sa_propre_ligne_et_ses_ecritures_ne_touchent_pas_l_autre() throws Exception {
        UUID sa = salle(CENTRE_A, "SALLE_A", "Salle A", "NON");
        salle(CENTRE_B, "SALLE_B", "Salle B", "OUI");
        doPut(URL, json("{'joursOuverts':['LUNDI'],'sallesIsolement':['" + sa + "'],'patientsParInfirmier':7}"), adminA());
        doPut(URL, json("{'joursOuverts':['MARDI'],'patientsParInfirmier':3}"), adminB());
        golden("centre_A_apres_ecritures_200", doGet(URL, adminA()));
        golden("centre_B_apres_ecritures_200", doGet(URL, adminB()));
        etatBase("deux_centres_etat_base");
    }

    @Test
    void une_ligne_ancienne_est_completee_par_la_premiere_ecriture() throws Exception {
        jdbc.update("INSERT INTO planning_parametres (center_id, jours_ouverts, salles_isolement, patients_par_infirmier, "
                + "patients_par_poste_serie, updated_at) VALUES (?, 'LUNDI', 'valeur-historique', NULL, NULL, CURRENT_TIMESTAMP)", CENTRE_A);
        goldenSimple("ecriture_sur_ligne_ancienne_200", doPut(URL, json("{'joursOuverts':['MARDI']}"), adminA()));
        etatBase("ecriture_sur_ligne_ancienne_etat_base");
    }

    // ------------------------------------------------------------------ validations

    @Test
    void le_ratio_est_borne_de_1_a_20() throws Exception {
        for (int ratio : new int[]{-1, 0, 1, 20, 21}) {
            goldenSimple("ratio_" + (ratio < 0 ? "moins" + -ratio : String.valueOf(ratio)),
                    doPut(URL, json("{'joursOuverts':['LUNDI'],'patientsParInfirmier':" + ratio + "}"), adminA()));
        }
        etatBase("ratio_etat_base_apres_bornes");
    }

    @Test
    void les_patients_par_poste_et_par_serie_sont_bornes_de_1_a_10() throws Exception {
        for (int n : new int[]{-1, 0, 1, 10, 11}) {
            goldenSimple("poste_" + (n < 0 ? "moins" + -n : String.valueOf(n)),
                    doPut(URL, json("{'joursOuverts':['LUNDI'],'patientsParPosteEtSerie':" + n + "}"), adminA()));
        }
        etatBase("poste_etat_base_apres_bornes");
    }

    @Test
    void au_moins_un_jour_d_ouverture_est_requis_et_un_refus_n_ecrit_rien() throws Exception {
        UUID s1 = salle(CENTRE_A, "SALLE_ISO_1", "Isolement 1", "OUI");
        salle(CENTRE_A, "SALLE_2", "Salle 2", "NON");
        goldenSimple("refus_jours_absents", doPut(URL, json("{'patientsParInfirmier':4}"), adminA()));
        goldenSimple("refus_jours_nuls", doPut(URL, json("{'joursOuverts':null}"), adminA()));
        goldenSimple("refus_jours_vides", doPut(URL, json("{'joursOuverts':[],'sallesIsolement':['" + s1 + "']}"), adminA()));
        goldenSimple("refus_corps_objet_vide", doPut(URL, "{}", adminA()));
        assertEquals(0, lignes());
        etatBase("refus_jours_etat_base_inchange");
    }

    @Test
    void une_salle_d_isolement_etrangere_au_centre_ou_inconnue_est_refusee_sans_rien_ecrire() throws Exception {
        UUID sa = salle(CENTRE_A, "SALLE_A", "Salle A", "OUI");
        UUID sb = salle(CENTRE_B, "SALLE_B", "Salle B", "NON");
        goldenSimple("refus_salle_d_un_autre_centre", doPut(URL, json("{'joursOuverts':['LUNDI'],'sallesIsolement':['" + sb + "']}"), adminA()));
        goldenSimple("refus_salle_inconnue", doPut(URL, json("{'joursOuverts':['LUNDI'],'sallesIsolement':['"
                + "99997800-0000-0000-0000-00000000dead']}"), adminA()));
        goldenSimple("refus_une_bonne_salle_et_une_etrangere", doPut(URL, json("{'joursOuverts':['LUNDI'],'sallesIsolement':['"
                + sa + "','" + sb + "']}"), adminA()));
        assertEquals(0, lignes());
        etatBase("refus_salle_etat_base_inchange");
    }

    @Test
    void l_ordre_des_validations_jours_puis_ratio_puis_poste_puis_salles() throws Exception {
        UUID sb = salle(CENTRE_B, "SALLE_B", "Salle B", "NON");
        goldenSimple("ordre_tout_invalide_signale_les_jours", doPut(URL, json("{'joursOuverts':[],'patientsParInfirmier':0,"
                + "'patientsParPosteEtSerie':0,'sallesIsolement':['" + sb + "']}"), adminA()));
        goldenSimple("ordre_ratio_avant_poste", doPut(URL, json("{'joursOuverts':['LUNDI'],'patientsParInfirmier':0,"
                + "'patientsParPosteEtSerie':0,'sallesIsolement':['" + sb + "']}"), adminA()));
        goldenSimple("ordre_poste_avant_salle", doPut(URL, json("{'joursOuverts':['LUNDI'],'patientsParPosteEtSerie':0,"
                + "'sallesIsolement':['" + sb + "']}"), adminA()));
    }

    @Test
    void les_corps_illisibles_sont_refuses() throws Exception {
        goldenSimple("refus_jour_inconnu", doPut(URL, json("{'joursOuverts':['FUNDI']}"), adminA()));
        goldenSimple("refus_jour_en_minuscules", doPut(URL, json("{'joursOuverts':['lundi']}"), adminA()));
        goldenSimple("refus_salle_pas_un_uuid", doPut(URL, json("{'joursOuverts':['LUNDI'],'sallesIsolement':['abc']}"), adminA()));
        goldenSimple("refus_ratio_texte", doPut(URL, json("{'joursOuverts':['LUNDI'],'patientsParInfirmier':'beaucoup'}"), adminA()));
        goldenSimple("refus_json_malforme", doPut(URL, "{'joursOuverts':", adminA()));
        goldenSimple("refus_corps_absent", send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put(java.net.URI.create(URL)).with(adminA())));
        goldenSimple("refus_content_type_texte", send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put(java.net.URI.create(URL)).contentType(org.springframework.http.MediaType.TEXT_PLAIN).content("x").with(adminA())));
        assertEquals(0, lignes());
    }

    @Test
    void un_ratio_decimal_est_tronque_a_l_entier_sans_erreur() throws Exception {
        goldenSimple("ratio_decimal_4_5_tronque_200", doPut(URL, json("{'joursOuverts':['LUNDI'],'patientsParInfirmier':4.9}"), adminA()));
        goldenSimple("ratio_decimal_20_5_tronque_et_accepte_200",
                doPut(URL, json("{'joursOuverts':['LUNDI'],'patientsParInfirmier':20.9}"), adminA()));
        goldenSimple("ratio_decimal_0_9_tronque_a_0_refuse",
                doPut(URL, json("{'joursOuverts':['LUNDI'],'patientsParInfirmier':0.9}"), adminA()));
    }

    @Test
    void un_corps_avec_des_champs_inconnus_est_accepte() throws Exception {
        goldenSimple("champ_inconnu_ignore_200", doPut(URL, json("{'joursOuverts':['LUNDI'],'couleur':'rouge'}"), adminA()));
    }

    // ------------------------------------------------------------------ garde de centre

    @Test
    void le_centre_demande_doit_etre_celui_de_la_session() throws Exception {
        UUID sb = salle(CENTRE_B, "SALLE_B", "Salle B", "NON");
        goldenSimple("lecture_centre_B_par_admin_A", doGet(URL + "?centerId=" + CENTRE_B, adminA()));
        goldenSimple("ecriture_centre_B_par_admin_A", doPut(URL + "?centerId=" + CENTRE_B,
                json("{'joursOuverts':['LUNDI'],'sallesIsolement':['" + sb + "']}"), adminA()));
        golden("lecture_centre_A_explicite_200", doGet(URL + "?centerId=" + CENTRE_A, adminA()));
        goldenSimple("ecriture_centre_A_explicite_200", doPut(URL + "?centerId=" + CENTRE_A, json("{'joursOuverts':['MARDI']}"), adminA()));
        goldenSimple("lecture_centre_illisible", doGet(URL + "?centerId=abc", adminA()));
        assertEquals(1, lignes());
        etatBase("garde_de_centre_etat_base");
    }

    @Test
    void le_superadmin_seul_est_refuse_par_le_filtre_de_perimetre_mais_avec_admin_il_agit_sur_n_importe_quel_centre() throws Exception {
        UUID sb = salle(CENTRE_B, "SALLE_B", "Salle B", "NON");
        goldenSimple("superadmin_seul_ecrit_pour_le_centre_B", doPut(URL + "?centerId=" + CENTRE_B,
                json("{'joursOuverts':['VENDREDI'],'sallesIsolement':['" + sb + "']}"), as(CENTRE_A, "SUPERADMIN")));
        goldenSimple("superadmin_seul_lit_le_centre_B", doGet(URL + "?centerId=" + CENTRE_B, as(CENTRE_A, "SUPERADMIN")));
        goldenSimple("superadmin_sans_centre_de_session_avec_centerId", doGet(URL + "?centerId=" + CENTRE_B, as(null, "SUPERADMIN")));
        goldenSimple("superadmin_sans_centre_de_session_sans_centerId", doGet(URL, as(null, "SUPERADMIN")));
        goldenSimple("superadmin_et_admin_ecrit_pour_le_centre_B_200", doPut(URL + "?centerId=" + CENTRE_B,
                json("{'joursOuverts':['VENDREDI'],'sallesIsolement':['" + sb + "']}"), as(CENTRE_A, "SUPERADMIN", "ADMIN")));
        goldenSimple("superadmin_et_admin_lit_le_centre_B_200", doGet(URL + "?centerId=" + CENTRE_B, as(CENTRE_A, "SUPERADMIN", "ADMIN")));
        goldenSimple("superadmin_et_admin_sans_centre_de_session_et_sans_centerId", doGet(URL, as(null, "SUPERADMIN", "ADMIN")));
        etatBase("superadmin_etat_base");
    }
}
