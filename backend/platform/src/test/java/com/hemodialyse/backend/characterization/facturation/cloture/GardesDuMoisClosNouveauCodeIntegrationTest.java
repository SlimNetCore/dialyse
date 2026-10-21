package com.hemodialyse.backend.characterization.facturation.cloture;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ce qu'un mois clos refuse (RG-FAC-041) et ce qu'il laisse permis : la validation, l'exclusion, la réinclusion et le changement
 * de forfait sont refusés 422 {@code PERIODE_DE_FACTURATION_CLOTUREE}, sans rien modifier ; la simulation, les lectures et les
 * règlements restent permis ; les autres mois et les autres centres ne sont pas touchés.
 */
class GardesDuMoisClosNouveauCodeIntegrationTest extends ClotureCharacterizationSupport {

    @Override
    protected String theme() {
        return "nouveau_code";
    }

    private static String exclusion(UUID centre, String mois) {
        return json("{'centerId':'" + centre + "','userId':'u1','motif':'MALADIE','month':'" + mois
                + "','regroupementMultiForfait':true}");
    }

    private static String reinclusion(UUID centre, String mois) {
        return json("{'centerId':'" + centre + "','userId':'u1','month':'" + mois + "','regroupementMultiForfait':true}");
    }

    private static String changementDeForfait(UUID centre, UUID forfait, String mois) {
        return json("{'centerId':'" + centre + "','userId':'medecin','forfaitId':'" + forfait + "','month':'" + mois
                + "','regroupementMultiForfait':true}");
    }

    @Test
    void la_validation_d_un_mois_clos_est_refusee_sans_emettre_ni_avancer_la_sequence() throws Exception {
        seance("AMRANI_03", P_AMRANI, CENTRE_A, "2026-08-03", "VALIDEE");
        seance("BELKACEM_04", P_BELKACEM, CENTRE_A, "2026-08-04", "SIGNEE");
        cloturer(AOUT, adminA());

        golden("validation_mois_clos_422", valider(CENTRE_A, AOUT, adminA()));

        goldenFacturation("validation_mois_clos_etat", CENTRE_A);
    }

    @Test
    void la_validation_par_dates_est_refusee_des_qu_un_mois_couvert_est_clos() throws Exception {
        seance("AMRANI_20_JUILLET", P_AMRANI, CENTRE_A, "2026-07-20", "VALIDEE");
        seance("BELKACEM_10_AOUT", P_BELKACEM, CENTRE_A, "2026-08-10", "VALIDEE");
        seance("AMRANI_05_SEPTEMBRE", P_AMRANI, CENTRE_A, "2026-09-05", "VALIDEE");
        cloturer(AOUT, adminA());

        golden("validation_intervalle_touchant_un_mois_clos_422",
                doPost("/validate", validationParDates(CENTRE_A, "2026-07-15", "2026-09-15"), adminA()));
        golden("validation_intervalle_ne_touchant_pas_le_mois_clos_200",
                doPost("/validate", validationParDates(CENTRE_A, "2026-09-01", "2026-09-30"), adminA()));

        goldenFacturation("validation_intervalle_etat", CENTRE_A);
    }

    @Test
    void un_intervalle_d_un_jour_dans_le_mois_clos_est_refuse() throws Exception {
        seance("AMRANI_31_AOUT", P_AMRANI, CENTRE_A, "2026-08-31", "VALIDEE");
        cloturer(AOUT, adminA());

        golden("validation_un_jour_dans_le_mois_clos_422",
                doPost("/validate", validationParDates(CENTRE_A, "2026-08-31", "2026-08-31"), adminA()));
    }

    @Test
    void la_validation_d_un_mois_clos_est_refusee_meme_sans_seance_a_emettre() throws Exception {
        cloturer(AOUT, adminA());

        golden("validation_mois_clos_sans_seance_422", valider(CENTRE_A, AOUT, adminA()));
    }

    @Test
    void les_autres_mois_et_les_autres_centres_se_valident_normalement() throws Exception {
        seance("AMRANI_03", P_AMRANI, CENTRE_A, "2026-08-03", "VALIDEE");
        seance("BELKACEM_02_SEPTEMBRE", P_BELKACEM, CENTRE_A, "2026-09-02", "VALIDEE");
        seance("ZERROUKI_03", P_CENTRE_B, CENTRE_B, "2026-08-03", "VALIDEE");
        cloturer(AOUT, adminA());

        golden("validation_autre_mois_200", valider(CENTRE_A, SEPTEMBRE, adminA()));
        golden("validation_autre_centre_meme_mois_200", valider(CENTRE_B, AOUT, adminB()));

        goldenFacturation("validation_autre_mois_etat_centre_a", CENTRE_A);
        goldenFacturation("validation_autre_centre_etat_centre_b", CENTRE_B);
    }

    @Test
    void la_validation_redevient_possible_apres_la_reouverture() throws Exception {
        seance("AMRANI_03", P_AMRANI, CENTRE_A, "2026-08-03", "VALIDEE");
        cloturer(AOUT, adminA());
        assertEquals(422, valider(CENTRE_A, AOUT, adminA()).status());
        assertEquals(200, rouvrir(AOUT, "oubli", adminA()).status());

        golden("validation_apres_reouverture_200", valider(CENTRE_A, AOUT, adminA()));
    }

    @Test
    void l_exclusion_d_une_seance_d_un_mois_clos_est_refusee_sans_declarer_d_absence() throws Exception {
        UUID seance = seance("AMRANI_03", P_AMRANI, CENTRE_A, "2026-08-03", "VALIDEE");
        cloturer(AOUT, adminA());

        golden("exclusion_mois_clos_422", doPost("/preview/seances/" + seance + "/exclude", exclusion(CENTRE_A, AOUT), adminA()));

        goldenFacturation("exclusion_mois_clos_etat", CENTRE_A);
    }

    @Test
    void la_reinclusion_d_une_seance_d_un_mois_clos_est_refusee() throws Exception {
        UUID seance = seance("AMRANI_03", P_AMRANI, CENTRE_A, "2026-08-03", "VALIDEE");
        // exclusion posée par le décor (une déclaration d'absence a un délai maximal, RG-ABS-011 : le jour d'exécution ne doit pas compter)
        jdbc.update("UPDATE seances SET statut = 'ABSENT', statut_avant_exclusion = 'VALIDEE' WHERE id = ?", seance);
        cloturer(AOUT, adminA());

        golden("reinclusion_mois_clos_422", doPost("/preview/seances/" + seance + "/include", reinclusion(CENTRE_A, AOUT), adminA()));

        goldenFacturation("reinclusion_mois_clos_etat", CENTRE_A);
    }

    @Test
    void le_changement_de_forfait_d_une_seance_d_un_mois_clos_est_refuse() throws Exception {
        UUID seance = seance("AMRANI_03", P_AMRANI, CENTRE_A, "2026-08-03", "VALIDEE");
        cloturer(AOUT, adminA());

        golden("forfait_mois_clos_422",
                doPost("/preview/seances/" + seance + "/forfait", changementDeForfait(CENTRE_A, FORFAIT_PREMIUM, AOUT), adminA()));

        goldenFacturation("forfait_mois_clos_etat", CENTRE_A);
    }

    @Test
    void c_est_la_date_de_la_seance_qui_compte_pas_la_periode_demandee() throws Exception {
        UUID dAout = seance("AMRANI_03_AOUT", P_AMRANI, CENTRE_A, "2026-08-03", "VALIDEE");
        UUID deSeptembre = seance("AMRANI_03_SEPTEMBRE", P_AMRANI, CENTRE_A, "2026-09-03", "VALIDEE");
        cloturer(AOUT, adminA());

        // la période demandée est septembre, mais la séance exclue est d'août : refusé
        golden("exclusion_seance_d_aout_periode_de_septembre_422",
                doPost("/preview/seances/" + dAout + "/exclude", exclusion(CENTRE_A, SEPTEMBRE), adminA()));
        // la période demandée est août (clos), mais la séance changée est de septembre : permis
        golden("forfait_seance_de_septembre_periode_d_aout_200",
                doPost("/preview/seances/" + deSeptembre + "/forfait", changementDeForfait(CENTRE_A, FORFAIT_PREMIUM, AOUT), adminA()));
    }

    @Test
    void la_simulation_d_un_mois_clos_reste_permise() throws Exception {
        seance("AMRANI_03", P_AMRANI, CENTRE_A, "2026-08-03", "VALIDEE");
        cloturer(AOUT, adminA());

        golden("simulation_mois_clos_200", doPost("/preview", json("{'centerId':'" + CENTRE_A + "','month':'" + AOUT
                + "','regroupementMultiForfait':true}"), adminA()));
    }

    @Test
    void les_reglements_d_une_facture_d_un_mois_clos_ne_sont_pas_bloques() throws Exception {
        UUID facture = factureExistante("AOUT", CENTRE_A, P_AMRANI, "ANCIENNE-0001", "2026-08-01", "2026-08-31");
        cloturer(AOUT, adminA());

        Resp reponse = doPostBrut("/api/v1/reglements/" + facture + "/paiements",
                json("{'centerId':'" + CENTRE_A + "','montant':50.00,'dateReglement':'2026-08-20','modeReglement':'BANQUE'}"),
                adminA());

        assertEquals(200, reponse.status());
        assertEquals(1, compter("facture_reglements", CENTRE_A));
    }

    @Test
    void les_lectures_de_facturation_d_un_mois_clos_restent_permises() throws Exception {
        cloturer(AOUT, adminA());

        assertEquals(200, doGet("/periodes?annee=2026", adminA()).status());
    }
}
