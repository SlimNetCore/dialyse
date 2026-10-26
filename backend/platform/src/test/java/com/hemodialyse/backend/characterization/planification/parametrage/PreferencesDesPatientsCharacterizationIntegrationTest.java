package com.hemodialyse.backend.characterization.planification.parametrage;

import com.hemodialyse.backend.characterization.planification.PlanificationCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation P1 : préférences de planification des patients,
 * {@code GET /api/v1/planning/preferences/patients} et {@code PUT /api/v1/planning/preferences/patients/{patientId}}
 * (RG-PLN-093, RG-PLN-094). Points d'observation : HTTP et état SQL de {@code planning_preference_patient}.
 */
class PreferencesDesPatientsCharacterizationIntegrationTest extends PlanificationCharacterizationSupport {

    private static final String URL = PLANNING + "/preferences/patients";

    @Override
    protected String theme() {
        return "p1-parametrage/preferences-patients";
    }

    private void etatBase(String name) {
        goldenSql(name, "SELECT center_id, patient_id, creneau_prefere_id, seances_par_semaine, jours_a_choisir "
                + "FROM planning_preference_patient WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B
                + "') ORDER BY center_id, patient_id");
    }

    private int lignes() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM planning_preference_patient WHERE center_id IN (?, ?)", Integer.class,
                CENTRE_A, CENTRE_B);
    }

    private static String corps(UUID creneau, Integer seances, boolean choisir) {
        return json("{'creneauPrefereId':" + (creneau == null ? "null" : "'" + creneau + "'") + ",'seancesParSemaine':"
                + seances + ",'joursAChoisir':" + choisir + "}");
    }

    @Test
    void la_liste_couvre_les_patients_non_sortis_du_centre_triee_par_nom_et_prenom_avec_une_preference_par_defaut() throws Exception {
        UUID matin = creneau(CENTRE_A, "CRENEAU_MATIN", "Matin");
        creneau(CENTRE_B, "CRENEAU_B", "Creneau B");
        UUID p1 = patient(CENTRE_A, "PATIENT_BENALI", "Benali", "Karim", null, matin, "lundi", "mercredi", "vendredi");
        patient(CENTRE_A, "PATIENT_AMRANI_Z", "Amrani", "Zahra", "ACTIF", null, "mardi");
        patient(CENTRE_A, "PATIENT_AMRANI_A", "Amrani", "Amine", "EN_ATTENTE", null);
        patient(CENTRE_A, "PATIENT_TRANSFERE", "Chikhi", "Nadia", "TRANSFERE", null, "lundi");
        patient(CENTRE_A, "PATIENT_DECEDE", "Dahmani", "Yanis", "DECEDE", null);
        patient(CENTRE_A, "PATIENT_GREFFE", "Ferhat", "Lina", "GREFFE", null);
        patient(CENTRE_A, "PATIENT_GUERRI", "Guerri", "Omar", "GUERRI", null);
        patient(CENTRE_B, "PATIENT_B", "Zerrouki", "Rania", null, null);
        jdbc.update("INSERT INTO planning_preference_patient (center_id, patient_id, creneau_prefere_id, seances_par_semaine, "
                + "jours_a_choisir) VALUES (?, ?, ?, 3, TRUE)", CENTRE_A, p1, matin);
        golden("liste_200", doGet(URL, adminA()));
        golden("liste_centre_B_200", doGet(URL, adminB()));
        assertEquals(1, lignes());
    }

    @Test
    void le_nom_affiche_est_prenom_puis_nom_et_un_prenom_vide_laisse_le_nom_seul() throws Exception {
        patient(CENTRE_A, "PATIENT_SANS_PRENOM", "Benali", "", null, null);
        golden("liste_prenom_absent_200", doGet(URL, adminA()));
    }

    @Test
    void la_pagination_est_en_nombre_de_pages_commencant_a_0_et_taille_20_par_defaut() throws Exception {
        for (int i = 1; i <= 5; i++) patient(CENTRE_A, "PATIENT_" + i, "Nom" + i, "Prenom", null, null);
        golden("pagination_page_0_taille_2_200", doGet(URL + "?page=0&size=2", adminA()));
        golden("pagination_page_2_taille_2_200", doGet(URL + "?page=2&size=2", adminA()));
        golden("pagination_page_hors_limite_200", doGet(URL + "?page=9&size=2", adminA()));
        goldenSimple("pagination_taille_0", doGet(URL + "?size=0", adminA()));
        goldenSimple("pagination_taille_negative", doGet(URL + "?size=-1", adminA()));
        goldenSimple("pagination_page_negative", doGet(URL + "?page=-1", adminA()));
        goldenSimple("pagination_taille_non_numerique", doGet(URL + "?size=abc", adminA()));
    }

    @Test
    void l_enregistrement_cree_puis_remplace_la_preference_du_patient_et_la_liste_la_reflete() throws Exception {
        UUID soir = creneau(CENTRE_A, "CRENEAU_SOIR", "Soir");
        UUID matin = creneau(CENTRE_A, "CRENEAU_MATIN", "Matin");
        UUID p = patient(CENTRE_A, "PATIENT_BENALI", "Benali", "Karim", null, matin, "lundi");
        goldenSimple("creation_200", doPut(URL + "/" + p, corps(soir, 3, true), adminA()));
        etatBase("creation_etat_base");
        goldenSimple("idempotence_second_appel_identique_200", doPut(URL + "/" + p, corps(soir, 3, true), adminA()));
        goldenSimple("remplacement_200", doPut(URL + "/" + p, corps(matin, 2, false), adminA()));
        etatBase("remplacement_etat_base");
        golden("liste_apres_ecritures_200", doGet(URL, adminA()));
        assertEquals(1, lignes());
    }

    @Test
    void une_preference_vide_efface_le_creneau_et_les_seances_sans_supprimer_la_ligne() throws Exception {
        UUID soir = creneau(CENTRE_A, "CRENEAU_SOIR", "Soir");
        UUID p = patient(CENTRE_A, "PATIENT_BENALI", "Benali", "Karim", null, null);
        doPut(URL + "/" + p, corps(soir, 4, true), adminA());
        goldenSimple("effacement_200", doPut(URL + "/" + p, json("{'joursAChoisir':false}"), adminA()));
        etatBase("effacement_etat_base");
        goldenSimple("corps_avec_champs_inconnus_200", doPut(URL + "/" + p, json("{'joursAChoisir':false,'couleur':'rouge'}"), adminA()));
    }

    @Test
    void les_seances_par_semaine_sont_bornees_de_1_a_7_et_obligatoires_pour_choisir_les_jours() throws Exception {
        UUID p = patient(CENTRE_A, "PATIENT_BENALI", "Benali", "Karim", null, null);
        for (int n : new int[]{0, 1, 7, 8}) goldenSimple("seances_" + n, doPut(URL + "/" + p, corps(null, n, false), adminA()));
        goldenSimple("refus_jours_a_choisir_sans_seances", doPut(URL + "/" + p, corps(null, null, true), adminA()));
        goldenSimple("jours_a_choisir_avec_seances_200", doPut(URL + "/" + p, corps(null, 3, true), adminA()));
        etatBase("seances_etat_base");
    }

    @Test
    void un_creneau_d_un_autre_centre_ou_inconnu_est_refuse_avant_la_recherche_du_patient() throws Exception {
        UUID autreCreneau = creneau(CENTRE_B, "CRENEAU_B", "Creneau B");
        UUID p = patient(CENTRE_A, "PATIENT_BENALI", "Benali", "Karim", null, null);
        UUID inconnu = UUID.fromString("99997800-0000-0000-0000-00000000dead");
        goldenSimple("refus_creneau_d_un_autre_centre", doPut(URL + "/" + p, corps(autreCreneau, 3, false), adminA()));
        goldenSimple("refus_creneau_inconnu", doPut(URL + "/" + p, corps(inconnu, 3, false), adminA()));
        goldenSimple("refus_creneau_inconnu_et_patient_inconnu_signale_le_creneau", doPut(URL + "/" + inconnu, corps(inconnu, 3, false), adminA()));
        assertEquals(0, lignes());
    }

    @Test
    void un_patient_inconnu_ou_d_un_autre_centre_est_introuvable() throws Exception {
        UUID pb = patient(CENTRE_B, "PATIENT_B", "Zerrouki", "Rania", null, null);
        goldenSimple("refus_patient_d_un_autre_centre", doPut(URL + "/" + pb, corps(null, 3, false), adminA()));
        goldenSimple("refus_patient_inconnu", doPut(URL + "/99997800-0000-0000-0000-00000000dead", corps(null, 3, false), adminA()));
        goldenSimple("refus_patient_identifiant_illisible", doPut(URL + "/abc", corps(null, 3, false), adminA()));
        assertEquals(0, lignes());
    }

    @Test
    void l_enregistrement_ne_verifie_que_l_existence_du_patient_meme_sorti() throws Exception {
        UUID transfere = patient(CENTRE_A, "PATIENT_TRANSFERE", "Chikhi", "Nadia", "TRANSFERE", null);
        goldenSimple("patient_transfere_accepte_200", doPut(URL + "/" + transfere, corps(null, 3, false), adminA()));
        golden("liste_n_affiche_pas_le_patient_sorti_200", doGet(URL, adminA()));
        etatBase("patient_sorti_etat_base");
    }

    @Test
    void le_corps_est_obligatoire_et_les_types_sont_controles() throws Exception {
        UUID p = patient(CENTRE_A, "PATIENT_BENALI", "Benali", "Karim", null, null);
        goldenSimple("refus_corps_absent", send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put(java.net.URI.create(URL + "/" + p)).with(adminA())));
        goldenSimple("refus_corps_vide_car_le_booleen_primitif_est_absent", doPut(URL + "/" + p, "{}", adminA()));
        goldenSimple("refus_seances_seules_car_le_booleen_primitif_est_absent", doPut(URL + "/" + p, json("{'seancesParSemaine':3}"), adminA()));
        goldenSimple("refus_creneau_pas_un_uuid", doPut(URL + "/" + p, json("{'creneauPrefereId':'abc'}"), adminA()));
        goldenSimple("refus_seances_texte", doPut(URL + "/" + p, json("{'seancesParSemaine':'trois'}"), adminA()));
        goldenSimple("refus_booleen_texte", doPut(URL + "/" + p, json("{'joursAChoisir':'peut-etre'}"), adminA()));
        assertEquals(0, lignes());
    }

    @Test
    void le_centre_demande_doit_etre_celui_de_la_session() throws Exception {
        UUID pa = patient(CENTRE_A, "PATIENT_A", "Benali", "Karim", null, null);
        UUID pb = patient(CENTRE_B, "PATIENT_B", "Zerrouki", "Rania", null, null);
        goldenSimple("lecture_centre_B_par_admin_A", doGet(URL + "?centerId=" + CENTRE_B, adminA()));
        goldenSimple("ecriture_centre_B_par_admin_A", doPut(URL + "/" + pb + "?centerId=" + CENTRE_B, corps(null, 3, false), adminA()));
        goldenSimple("ecriture_centre_A_explicite_200", doPut(URL + "/" + pa + "?centerId=" + CENTRE_A, corps(null, 3, false), adminA()));
        goldenSimple("superadmin_seul_ecrit_pour_le_centre_B", doPut(URL + "/" + pb + "?centerId=" + CENTRE_B, corps(null, 2, false),
                as(CENTRE_A, "SUPERADMIN")));
        etatBase("garde_de_centre_etat_base");
    }
}
