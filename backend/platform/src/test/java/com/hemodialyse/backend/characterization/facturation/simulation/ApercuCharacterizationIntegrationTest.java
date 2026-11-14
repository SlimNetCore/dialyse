package com.hemodialyse.backend.characterization.facturation.simulation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * Filet de caractérisation F2 — aperçu de facturation {@code POST /facturation/preview} : séances éligibles, forfait
 * appliqué, regroupement, lignes, totaux, TVA, numéros prévisionnels, période (RG-FAC-020 à 023).
 */
class ApercuCharacterizationIntegrationTest extends SimulationCharacterizationSupport {

    @Override
    protected String theme() {
        return "apercu";
    }

    @Test
    void un_centre_sans_seance_eligible_rend_un_apercu_vide() throws Exception {
        golden("aucune_seance_200", doPost("/preview", mois(CENTRE_A, "2026-08", true), adminA()));
    }

    @Test
    void seules_les_seances_validees_ou_signees_non_facturees_de_la_periode_et_du_centre_sont_retenues() throws Exception {
        seance("VALIDEE_02", P_EFFECTIF, CENTRE_A, "2026-08-02", "VALIDEE");
        seance("SIGNEE_03", P_EFFECTIF, CENTRE_A, "2026-08-03", "SIGNEE");
        seance("BROUILLON_04", P_EFFECTIF, CENTRE_A, "2026-08-04", "BROUILLON");
        seance("ABSENT_05", P_EFFECTIF, CENTRE_A, "2026-08-05", "ABSENT");
        seance("FACTUREE_06", P_EFFECTIF, CENTRE_A, "2026-08-06", "FACTUREE");
        seance("VALIDEE_minuscule_07", P_EFFECTIF, CENTRE_A, "2026-08-07", "validee");
        seance("VEILLE_31_07", P_EFFECTIF, CENTRE_A, "2026-07-31", "VALIDEE");
        seance("BORNE_01_08", P_EFFECTIF, CENTRE_A, "2026-08-01", "VALIDEE");
        seance("BORNE_31_08", P_EFFECTIF, CENTRE_A, "2026-08-31", "VALIDEE");
        seance("LENDEMAIN_01_09", P_EFFECTIF, CENTRE_A, "2026-09-01", "VALIDEE");
        seance("CENTRE_B", P_CENTRE_B, CENTRE_B, "2026-08-02", "VALIDEE");
        golden("eligibilite_200", doPost("/preview", mois(CENTRE_A, "2026-08", true), adminA()));
    }

    @Test
    void le_forfait_applique_suit_la_surcharge_puis_la_pec_couvrant_la_date_puis_la_pec_la_plus_recente() throws Exception {
        seance("EFFECTIF_02", P_EFFECTIF, CENTRE_A, "2026-08-02", "VALIDEE");
        UUID surchargee = seance("SURCHARGEE_04", P_EFFECTIF, CENTRE_A, "2026-08-04", "VALIDEE");
        surcharge(surchargee, FORFAIT_PREMIUM, "Premium (surcharge)", "6000.00");
        seance("EFFECTIF_06", P_EFFECTIF, CENTRE_A, "2026-08-06", "VALIDEE");
        seance("DEMANDE_02", P_DEMANDE, CENTRE_A, "2026-08-02", "SIGNEE");
        seance("SANS_PEC_03", P_SANS_PEC, CENTRE_A, "2026-08-03", "VALIDEE");
        seance("PEC_ECHUE_05", P_PEC_ECHUE, CENTRE_A, "2026-08-05", "VALIDEE");
        golden("forfaits_regroupement_multi_forfait_200", doPost("/preview", mois(CENTRE_A, "2026-08", true), adminA()));
        golden("forfaits_une_facture_par_forfait_200", doPost("/preview", mois(CENTRE_A, "2026-08", false), adminA()));
    }

    /**
     * LEC-PAT (ii), ARGENT : l'ordre des séances à facturer est celui du nom puis du prénom de la fiche (homonymes départagés par la date),
     * une séance du centre posée sur une fiche d'un autre centre n'est pas facturable, un patient transféré l'est, un patient sans prise
     * en charge est facturé sur un forfait non défini. Posé AVANT de remplacer la lecture SQL de {@code patients} par le langage publié.
     */
    @Test
    void l_ordre_des_seances_a_facturer_suit_le_nom_puis_le_prenom_de_la_fiche_du_centre() throws Exception {
        UUID h1 = UUID.fromString("99997000-0000-0000-0000-00000000a0e1");
        UUID h2 = UUID.fromString("99997000-0000-0000-0000-00000000a0e2");
        UUID h3 = UUID.fromString("99997000-0000-0000-0000-00000000a0e3");
        UUID h4 = UUID.fromString("99997000-0000-0000-0000-00000000a0e4");
        nommer(h1, "<H1_BENALI_KARIM>");
        nommer(h2, "<H2_BENALI_KARIM>");
        nommer(h3, "<H3_AOUAD_TRANSFERE>");
        nommer(h4, "<H4_BENALI_AMEL>");
        patient(h1, CENTRE_A, "SIM-H1", "Benali", "Karim");
        patient(h2, CENTRE_A, "SIM-H2", "Benali", "Karim");
        patient(h3, CENTRE_A, "SIM-H3", "Aouad", "Zied");
        patient(h4, CENTRE_A, "SIM-H4", "Benali", "Amel");
        jdbc.update("UPDATE patients SET etat_patient = 'TRANSFERE' WHERE id = ?", h3);
        pec(h1, CENTRE_A, "2026-01-01", "2026-12-31", "2026-01-01", "2026-12-31", FORFAIT_PREMIUM, FORFAIT_HD, 1);
        pec(h2, CENTRE_A, "2026-01-01", "2026-12-31", null, null, FORFAIT_PREMIUM, null, 1);
        pec(h4, CENTRE_A, "2026-01-01", "2026-06-30", "2026-01-01", "2026-06-30", FORFAIT_HD, FORFAIT_HD, 1);
        seance("H1_05", h1, CENTRE_A, "2026-08-05", "VALIDEE");
        seance("H2_03", h2, CENTRE_A, "2026-08-03", "SIGNEE");
        seance("H2_07", h2, CENTRE_A, "2026-08-07", "VALIDEE");
        seance("H3_09", h3, CENTRE_A, "2026-08-09", "VALIDEE");
        seance("H4_02", h4, CENTRE_A, "2026-08-02", "VALIDEE");
        seance("EFFECTIF_06", P_EFFECTIF, CENTRE_A, "2026-08-06", "VALIDEE");
        seance("FICHE_AU_CENTRE_B_04", P_CENTRE_B, CENTRE_A, "2026-08-04", "VALIDEE");
        golden("ordre_nom_prenom_homonymes_fiche_autre_centre_regroupement_200", doPost("/preview", mois(CENTRE_A, "2026-08", true), adminA()));
        golden("ordre_nom_prenom_homonymes_fiche_autre_centre_par_forfait_200", doPost("/preview", mois(CENTRE_A, "2026-08", false), adminA()));
    }

    @Test
    void sans_type_de_tva_actif_le_taux_est_nul() throws Exception {
        jdbc.update("DELETE FROM tva_types WHERE center_id = ?", CENTRE_A);
        seance("EFFECTIF_02", P_EFFECTIF, CENTRE_A, "2026-08-02", "VALIDEE");
        golden("sans_tva_200", doPost("/preview", mois(CENTRE_A, "2026-08", true), adminA()));
    }

    @Test
    void la_periode_par_deux_dates_et_ses_refus() throws Exception {
        seance("DEBUT_01", P_EFFECTIF, CENTRE_A, "2026-08-01", "VALIDEE");
        seance("MILIEU_15", P_EFFECTIF, CENTRE_A, "2026-08-15", "VALIDEE");
        seance("APRES_16", P_EFFECTIF, CENTRE_A, "2026-08-16", "VALIDEE");
        golden("periode_deux_dates_200", doPost("/preview", json("{'centerId':'" + CENTRE_A
                + "','periodStart':'2026-08-01','periodEnd':'2026-08-15','regroupementMultiForfait':true}"), adminA()));
        golden("periode_mois_prioritaire_sur_les_dates_200", doPost("/preview", json("{'centerId':'" + CENTRE_A
                        + "','month':'2026-08','periodStart':'2026-08-01','periodEnd':'2026-08-01','regroupementMultiForfait':true}"),
                adminA()));
        golden("periode_fin_avant_debut", doPost("/preview", json("{'centerId':'" + CENTRE_A
                + "','periodStart':'2026-08-15','periodEnd':'2026-08-01','regroupementMultiForfait':true}"), adminA()));
        golden("periode_absente", doPost("/preview", json("{'centerId':'" + CENTRE_A + "','regroupementMultiForfait':true}"),
                adminA()));
        golden("periode_mois_illisible", doPost("/preview", mois(CENTRE_A, "2026-13", true), adminA()));
        golden("centre_absent", doPost("/preview", json("{'month':'2026-08','regroupementMultiForfait':true}"), adminA()));
        golden("regroupement_absent", doPost("/preview", json("{'centerId':'" + CENTRE_A + "','month':'2026-08'}"), adminA()));
    }

    @Test
    void autorisations_et_centres() throws Exception {
        seance("EFFECTIF_02", P_EFFECTIF, CENTRE_A, "2026-08-02", "VALIDEE");
        golden("autorisation_medecin", doPost("/preview", mois(CENTRE_A, "2026-08", true), as(CENTRE_A, "MEDECIN")));
        golden("autorisation_secretaire", doPost("/preview", mois(CENTRE_A, "2026-08", true), as(CENTRE_A, "SECRETAIRE")));
        golden("autorisation_infirmier", doPost("/preview", mois(CENTRE_A, "2026-08", true), as(CENTRE_A, "INFIRMIER")));
        golden("autorisation_anonyme", doPostAnonyme("/preview", mois(CENTRE_A, "2026-08", true)));
        // KB-FAC-14 corrigé : l'aperçu d'un autre centre que celui de la session est refusé (403) ; il était rendu (200)
        golden("centre_A_par_admin_B_nouveau_code", doPost("/preview", mois(CENTRE_A, "2026-08", true), as(CENTRE_B, "ADMIN")));
    }

    /**
     * KB-FAC-16 corrigé : le taux de TVA est celui en vigueur à la fin de la période facturée (il était celui du jour).
     */
    @Test
    void le_taux_de_tva_est_celui_de_la_fin_de_la_periode_nouveau_code() throws Exception {
        jdbc.update("DELETE FROM tva_types WHERE center_id = ?", CENTRE_A);
        tvaDu(CENTRE_A, "9.00", "2000-01-01", "2026-08-14");
        tvaDu(CENTRE_A, "19.00", "2026-08-15", null);
        tvaDu(CENTRE_A, "30.00", "2026-09-01", null);
        seance("EFFECTIF_02", P_EFFECTIF, CENTRE_A, "2026-08-02", "VALIDEE");
        golden("tva_fin_de_mois_19_200_nouveau_code", doPost("/preview", mois(CENTRE_A, "2026-08", true), adminA()));
        golden("tva_fin_de_periode_9_200_nouveau_code", doPost("/preview", json("{'centerId':'" + CENTRE_A
                + "','periodStart':'2026-08-01','periodEnd':'2026-08-10','regroupementMultiForfait':true}"), adminA()));
    }

    private void tvaDu(UUID centre, String taux, String debut, String fin) {
        jdbc.update("INSERT INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere, date_debut_validite, "
                        + "date_fin_validite, actif, created_by) VALUES (?, ?, 'TVA', ?, 'HEMODIALYSE', FALSE, ?, ?, TRUE, 'fixture')",
                UUID.randomUUID(), centre, new java.math.BigDecimal(taux), java.sql.Date.valueOf(debut),
                fin == null ? null : java.sql.Date.valueOf(fin));
    }
}
