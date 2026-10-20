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
