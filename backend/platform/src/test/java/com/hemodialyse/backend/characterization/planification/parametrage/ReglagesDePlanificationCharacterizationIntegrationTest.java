package com.hemodialyse.backend.characterization.planification.parametrage;

import com.hemodialyse.backend.characterization.planification.PlanificationCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation P1 : réglages de la planification du centre, {@code GET|PUT /api/v1/planning/preferences/reglages}
 * (RG-PLN-098). Points d'observation : HTTP et état SQL de {@code planification_reglages}.
 */
class ReglagesDePlanificationCharacterizationIntegrationTest extends PlanificationCharacterizationSupport {

    private static final String URL = PLANNING + "/preferences/reglages";

    @Override
    protected String theme() {
        return "p1-parametrage/reglages";
    }

    private void etatBase(String name) {
        goldenSql(name, "SELECT center_id, replanification_auto, heures_par_vacation, heures_hebdo_temps_plein, repos_hebdo_min "
                + "FROM planification_reglages WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY center_id");
    }

    private int lignes() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM planification_reglages WHERE center_id IN (?, ?)", Integer.class,
                CENTRE_A, CENTRE_B);
    }

    private static String corps(boolean auto, int vacation, int hebdo, int repos) {
        return json("{'replanificationAuto':" + auto + ",'heuresParVacation':" + vacation + ",'heuresHebdoTempsPlein':" + hebdo
                + ",'reposHebdoMin':" + repos + "}");
    }

    @Test
    void sans_ligne_les_reglages_lus_sont_les_defauts_et_rien_n_est_cree() throws Exception {
        golden("lecture_defauts_200", doGet(URL, adminA()));
        assertEquals(0, lignes());
    }

    @Test
    void l_enregistrement_cree_a_la_volee_puis_met_a_jour_la_ligne_unique_du_centre() throws Exception {
        goldenSimple("creation_a_la_volee_200", doPut(URL, corps(true, 8, 35, 2), adminA()));
        etatBase("creation_etat_base");
        goldenSimple("mise_a_jour_200", doPut(URL, corps(false, 12, 60, 6), adminA()));
        etatBase("mise_a_jour_etat_base");
        goldenSimple("idempotence_second_appel_identique_200", doPut(URL, corps(false, 12, 60, 6), adminA()));
        assertEquals(1, lignes());
        golden("relecture_200", doGet(URL, adminA()));
    }

    @Test
    void les_reglages_d_un_centre_sont_independants_de_ceux_de_l_autre() throws Exception {
        doPut(URL, corps(true, 6, 30, 3), adminA());
        golden("centre_B_garde_les_defauts_200", doGet(URL, adminB()));
        doPut(URL, corps(false, 4, 20, 0), adminB());
        golden("centre_A_inchange_200", doGet(URL, adminA()));
        etatBase("deux_centres_etat_base");
    }

    @Test
    void la_duree_d_une_vacation_est_bornee_de_1_a_12_heures() throws Exception {
        for (int h : new int[]{0, 1, 12, 13}) goldenSimple("vacation_" + h, doPut(URL, corps(false, h, 40, 1), adminA()));
        etatBase("vacation_etat_base");
    }

    @Test
    void les_heures_d_un_temps_plein_sont_bornees_de_10_a_60() throws Exception {
        for (int h : new int[]{9, 10, 60, 61}) goldenSimple("temps_plein_" + h, doPut(URL, corps(false, 5, h, 1), adminA()));
        etatBase("temps_plein_etat_base");
    }

    @Test
    void le_repos_hebdomadaire_est_borne_de_0_a_6_jours() throws Exception {
        for (int j : new int[]{-1, 0, 6, 7}) {
            goldenSimple("repos_" + (j < 0 ? "moins1" : String.valueOf(j)), doPut(URL, corps(false, 5, 40, j), adminA()));
        }
        etatBase("repos_etat_base");
    }

    @Test
    void un_corps_partiel_ou_illisible_repond_500_meme_pour_un_champ_primitif_absent() throws Exception {
        goldenSimple("refus_corps_vide", doPut(URL, "{}", adminA()));
        goldenSimple("refus_seul_le_booleen", doPut(URL, json("{'replanificationAuto':true}"), adminA()));
        goldenSimple("refus_sans_repos", doPut(URL, json("{'replanificationAuto':true,'heuresParVacation':5,"
                + "'heuresHebdoTempsPlein':40}"), adminA()));
        goldenSimple("refus_entier_nul", doPut(URL, json("{'heuresParVacation':null,'heuresHebdoTempsPlein':40,'reposHebdoMin':1}"), adminA()));
        goldenSimple("refus_booleen_texte", doPut(URL, json("{'replanificationAuto':'oui','heuresParVacation':5,"
                + "'heuresHebdoTempsPlein':40,'reposHebdoMin':1}"), adminA()));
        goldenSimple("refus_json_malforme", doPut(URL, "{", adminA()));
        goldenSimple("refus_booleen_absent", doPut(URL, json("{'heuresParVacation':5,'heuresHebdoTempsPlein':40,'reposHebdoMin':1}"), adminA()));
        etatBase("corps_partiel_etat_base");
    }

    @Test
    void les_valeurs_hors_bornes_repondent_500_quel_que_soit_le_champ_fautif() throws Exception {
        goldenSimple("tout_invalide", doPut(URL, corps(false, 0, 0, 9), adminA()));
        goldenSimple("temps_plein_et_repos_invalides", doPut(URL, corps(false, 5, 0, 9), adminA()));
        assertEquals(0, lignes());
    }

    @Test
    void la_lecture_est_permise_a_l_administrateur_et_au_secretaire_l_ecriture_a_l_administrateur_seul() throws Exception {
        Map<String, Object> matrice = new LinkedHashMap<>();
        for (Map.Entry<String, org.springframework.test.web.servlet.request.RequestPostProcessor> p : profils().entrySet()) {
            matrice.put("GET [" + p.getKey() + "]", statutEtCode(doGet(URL, p.getValue())));
            jdbc.update("DELETE FROM planification_reglages WHERE center_id = ?", CENTRE_A);
            String s = statutEtCode(doPut(URL, corps(true, 7, 38, 2), p.getValue()));
            matrice.put("PUT [" + p.getKey() + "]", s + " / " + (lignes() == 0 ? "rien ecrit" : "ecrit"));
        }
        matrice.put("GET [anonyme]", statutEtCode(doGetAnonymous(URL)));
        matrice.put("PUT [anonyme]", statutEtCode(doPutAnonymous(URL, corps(true, 7, 38, 2))));
        goldenNode("matrice_profils", mapper.valueToTree(matrice));
    }

    @Test
    void le_centre_demande_doit_etre_celui_de_la_session() throws Exception {
        goldenSimple("lecture_centre_B_par_admin_A", doGet(URL + "?centerId=" + CENTRE_B, adminA()));
        goldenSimple("ecriture_centre_B_par_admin_A", doPut(URL + "?centerId=" + CENTRE_B, corps(true, 5, 40, 1), adminA()));
        goldenSimple("ecriture_centre_A_explicite_200", doPut(URL + "?centerId=" + CENTRE_A, corps(true, 5, 40, 1), adminA()));
        goldenSimple("superadmin_seul_ecrit_pour_le_centre_B", doPut(URL + "?centerId=" + CENTRE_B, corps(false, 9, 45, 2),
                as(CENTRE_A, "SUPERADMIN")));
        etatBase("garde_de_centre_etat_base");
    }
}
