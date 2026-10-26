package com.hemodialyse.backend.characterization.planification.parametrage;

import com.hemodialyse.backend.characterization.planification.PlanificationCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation P1 : profils de planification des infirmiers,
 * {@code GET /api/v1/planning/preferences/infirmiers} et {@code PUT /api/v1/planning/preferences/infirmiers/{infirmierId}}
 * (RG-PLN-097). Points d'observation : HTTP et état SQL de {@code infirmier_profil_planning}.
 */
class ProfilsDesInfirmiersCharacterizationIntegrationTest extends PlanificationCharacterizationSupport {

    private static final String URL = PLANNING + "/preferences/infirmiers";

    @Override
    protected String theme() {
        return "p1-parametrage/profils-infirmiers";
    }

    private void etatBase(String name) {
        goldenSql(name, "SELECT center_id, infirmier_id, taux_activite, competences FROM infirmier_profil_planning "
                + "WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY center_id, infirmier_id");
    }

    private int lignes() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM infirmier_profil_planning WHERE center_id IN (?, ?)", Integer.class,
                CENTRE_A, CENTRE_B);
    }

    private static String corps(Object taux, String competences) {
        return json("{'tauxActivite':" + taux + ",'competences':" + competences + "}");
    }

    @Test
    void la_liste_couvre_les_infirmiers_actifs_du_centre_par_nom_avec_le_profil_par_defaut_temps_plein_sans_competence() throws Exception {
        UUID benali = infirmier(CENTRE_A, "INFIRMIER_BENALI", "Benali", "Samia", "INFIRMIER", true);
        infirmier(CENTRE_A, "INFIRMIER_AMRANI", "Amrani", null, "MAJOR", true);
        infirmier(CENTRE_A, "INFIRMIER_INACTIF", "Chikhi", "Nadia", "INFIRMIER", false);
        infirmier(CENTRE_B, "INFIRMIER_B", "Zerrouki", "Rania", "INFIRMIER", true);
        jdbc.update("INSERT INTO infirmier_profil_planning (center_id, infirmier_id, taux_activite, competences) "
                + "VALUES (?, ?, 60, 'PEDIATRIE,CATHETER')", CENTRE_A, benali);
        golden("liste_200", doGet(URL, adminA()));
        golden("liste_centre_B_200", doGet(URL, adminB()));
        assertEquals(1, lignes());
    }

    @Test
    void les_competences_stockees_nulles_blanches_ou_avec_un_element_vide_sont_lues_sans_erreur() throws Exception {
        UUID a = infirmier(CENTRE_A, "INFIRMIER_A", "Aaa", "Un", "INFIRMIER", true);
        UUID b = infirmier(CENTRE_A, "INFIRMIER_B", "Bbb", "Deux", "INFIRMIER", true);
        UUID c = infirmier(CENTRE_A, "INFIRMIER_C", "Ccc", "Trois", "INFIRMIER", true);
        jdbc.update("INSERT INTO infirmier_profil_planning (center_id, infirmier_id, taux_activite, competences) VALUES (?, ?, 30, NULL)", CENTRE_A, a);
        jdbc.update("INSERT INTO infirmier_profil_planning (center_id, infirmier_id, taux_activite, competences) VALUES (?, ?, 40, '   ')", CENTRE_A, b);
        jdbc.update("INSERT INTO infirmier_profil_planning (center_id, infirmier_id, taux_activite, competences) VALUES (?, ?, 50, ' CATHETER, ,PEDIATRIE')", CENTRE_A, c);
        golden("liste_competences_stockees_atypiques_200", doGet(URL, adminA()));
    }

    @Test
    void la_pagination_est_en_nombre_de_pages_commencant_a_0() throws Exception {
        for (int i = 1; i <= 3; i++) infirmier(CENTRE_A, "INFIRMIER_" + i, "Nom" + i, "Prenom", "INFIRMIER", true);
        golden("pagination_page_1_taille_2_200", doGet(URL + "?page=1&size=2", adminA()));
        golden("pagination_page_hors_limite_200", doGet(URL + "?page=5&size=2", adminA()));
        goldenSimple("pagination_taille_0", doGet(URL + "?size=0", adminA()));
        goldenSimple("pagination_taille_negative", doGet(URL + "?size=-1", adminA()));
    }

    @Test
    void l_enregistrement_cree_puis_remplace_le_profil_et_les_competences_sont_triees() throws Exception {
        UUID i = infirmier(CENTRE_A, "INFIRMIER_BENALI", "Benali", "Samia", "INFIRMIER", true);
        goldenSimple("creation_200", doPut(URL + "/" + i, corps(80, "['CATHETER','PEDIATRIE','CATHETER']"), adminA()));
        etatBase("creation_etat_base");
        goldenSimple("idempotence_second_appel_identique_200", doPut(URL + "/" + i, corps(80, "['PEDIATRIE','CATHETER']"), adminA()));
        goldenSimple("remplacement_200", doPut(URL + "/" + i, corps(50, "['PEDIATRIE']"), adminA()));
        etatBase("remplacement_etat_base");
        goldenSimple("competences_nulles_200", doPut(URL + "/" + i, corps(50, "null"), adminA()));
        etatBase("competences_nulles_etat_base");
        goldenSimple("competences_absentes_200", doPut(URL + "/" + i, json("{'tauxActivite':100}"), adminA()));
        golden("liste_apres_ecritures_200", doGet(URL, adminA()));
        assertEquals(1, lignes());
    }

    @Test
    void le_taux_d_activite_est_borne_de_10_a_100_et_obligatoire() throws Exception {
        UUID i = infirmier(CENTRE_A, "INFIRMIER_BENALI", "Benali", "Samia", "INFIRMIER", true);
        for (int t : new int[]{-1, 0, 9, 10, 100, 101}) {
            goldenSimple("taux_" + (t < 0 ? "moins1" : String.valueOf(t)), doPut(URL + "/" + i, corps(t, "[]"), adminA()));
        }
        goldenSimple("refus_taux_absent", doPut(URL + "/" + i, json("{'competences':[]}"), adminA()));
        goldenSimple("refus_taux_nul", doPut(URL + "/" + i, corps("null", "[]"), adminA()));
        goldenSimple("refus_taux_texte", doPut(URL + "/" + i, corps("'plein'", "[]"), adminA()));
        goldenSimple("refus_taux_decimal", doPut(URL + "/" + i, corps("75.5", "[]"), adminA()));
        etatBase("taux_etat_base");
    }

    @Test
    void les_competences_sont_pediatrie_et_catheter_seulement() throws Exception {
        UUID i = infirmier(CENTRE_A, "INFIRMIER_BENALI", "Benali", "Samia", "INFIRMIER", true);
        goldenSimple("refus_competence_inconnue", doPut(URL + "/" + i, corps(100, "['DIALYSE']"), adminA()));
        goldenSimple("refus_competence_en_minuscules", doPut(URL + "/" + i, corps(100, "['pediatrie']"), adminA()));
        goldenSimple("refus_competences_pas_une_liste", doPut(URL + "/" + i, corps(100, "'PEDIATRIE'"), adminA()));
        goldenSimple("refus_competence_nulle_dans_la_liste", doPut(URL + "/" + i, corps(100, "[null]"), adminA()));
        goldenSimple("refus_corps_absent", send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put(java.net.URI.create(URL + "/" + i)).with(adminA())));
        assertEquals(0, lignes());
    }

    @Test
    void un_infirmier_inconnu_ou_d_un_autre_centre_est_introuvable_et_un_infirmier_inactif_est_accepte() throws Exception {
        UUID autre = infirmier(CENTRE_B, "INFIRMIER_B", "Zerrouki", "Rania", "INFIRMIER", true);
        UUID inactif = infirmier(CENTRE_A, "INFIRMIER_INACTIF", "Chikhi", "Nadia", "INFIRMIER", false);
        goldenSimple("refus_infirmier_d_un_autre_centre", doPut(URL + "/" + autre, corps(100, "[]"), adminA()));
        goldenSimple("refus_infirmier_inconnu", doPut(URL + "/99997800-0000-0000-0000-00000000dead", corps(100, "[]"), adminA()));
        goldenSimple("refus_infirmier_identifiant_illisible", doPut(URL + "/abc", corps(100, "[]"), adminA()));
        goldenSimple("refus_taux_invalide_et_infirmier_inconnu_signale_le_taux", doPut(URL + "/99997800-0000-0000-0000-00000000dead",
                corps(5, "[]"), adminA()));
        goldenSimple("infirmier_inactif_accepte_200", doPut(URL + "/" + inactif, corps(70, "['CATHETER']"), adminA()));
        golden("liste_n_affiche_pas_l_inactif_200", doGet(URL, adminA()));
        etatBase("introuvables_etat_base");
    }

    @Test
    void le_centre_demande_doit_etre_celui_de_la_session() throws Exception {
        UUID ia = infirmier(CENTRE_A, "INFIRMIER_A", "Benali", "Samia", "INFIRMIER", true);
        UUID ib = infirmier(CENTRE_B, "INFIRMIER_B", "Zerrouki", "Rania", "INFIRMIER", true);
        goldenSimple("lecture_centre_B_par_admin_A", doGet(URL + "?centerId=" + CENTRE_B, adminA()));
        goldenSimple("ecriture_centre_B_par_admin_A", doPut(URL + "/" + ib + "?centerId=" + CENTRE_B, corps(100, "[]"), adminA()));
        goldenSimple("ecriture_centre_A_explicite_200", doPut(URL + "/" + ia + "?centerId=" + CENTRE_A, corps(90, "[]"), adminA()));
        goldenSimple("superadmin_seul_ecrit_pour_le_centre_B", doPut(URL + "/" + ib + "?centerId=" + CENTRE_B, corps(40, "['PEDIATRIE']"),
                as(CENTRE_A, "SUPERADMIN")));
        etatBase("garde_de_centre_etat_base");
    }
}
