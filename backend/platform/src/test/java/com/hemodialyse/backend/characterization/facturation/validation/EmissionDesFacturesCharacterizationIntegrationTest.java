package com.hemodialyse.backend.characterization.facturation.validation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * Filet de caractérisation F3 — émission nominale par {@code POST /facturation/validate} : regroupement, lignes, totaux
 * HT / TVA / TTC, instantanés (patient, payeur, agence, prix, taux), arrondis, TVA figée (RG-FAC-020 à 030).
 */
class EmissionDesFacturesCharacterizationIntegrationTest extends ValidationCharacterizationSupport {

    @Override
    protected String theme() {
        return "emission";
    }

    private void troisPatientsEnJanvier() {
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");
        seance("AMRANI_12", P_AMRANI, CENTRE_A, "2026-01-12", "SIGNEE");
        seance("AMRANI_19", P_AMRANI, CENTRE_A, "2026-01-19", "VALIDEE");
        seance("BELKACEM_06", P_BELKACEM, CENTRE_A, "2026-01-06", "VALIDEE");
        seance("BELKACEM_13", P_BELKACEM, CENTRE_A, "2026-01-13", "VALIDEE");
        seance("CHIKHI_07", P_CHIKHI, CENTRE_A, "2026-01-07", "SIGNEE");
    }

    @Test
    void la_validation_emet_une_facture_par_patient_avec_totaux_instantanes_et_taux_figes() throws Exception {
        troisPatientsEnJanvier();

        golden("nominale_regroupee_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("nominale_regroupee_etat", CENTRE_A);
    }

    @Test
    void sans_regroupement_chaque_patient_a_une_facture_par_forfait() throws Exception {
        troisPatientsEnJanvier();
        UUID surchargee = seance("AMRANI_26_PREMIUM", P_AMRANI, CENTRE_A, "2026-01-26", "VALIDEE");
        surcharge(surchargee, FORFAIT_PREMIUM, "Forfait HD Premium", "5200.00");

        golden("sans_regroupement_200", valider(mois(CENTRE_A, JANVIER, false)));
        goldenEtat("sans_regroupement_etat", CENTRE_A);
    }

    @Test
    void avec_regroupement_les_forfaits_d_un_patient_deviennent_les_lignes_d_une_seule_facture() throws Exception {
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");
        UUID surchargee = seance("AMRANI_26_PREMIUM", P_AMRANI, CENTRE_A, "2026-01-26", "VALIDEE");
        surcharge(surchargee, FORFAIT_PREMIUM, "Forfait HD Premium", "5200.00");
        UUID autreLibelle = seance("AMRANI_27_LIBELLE", P_AMRANI, CENTRE_A, "2026-01-27", "VALIDEE");
        surcharge(autreLibelle, FORFAIT_PREMIUM, "Premium negocie", "4800.00");

        golden("regroupement_lignes_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("regroupement_lignes_etat", CENTRE_A);
    }

    @Test
    void la_tva_est_arrondie_a_la_demi_superieure_sur_le_total_hors_taxe_de_la_facture() throws Exception {
        jdbc.update("UPDATE forfait SET prix = 33.33 WHERE id = ?", FORFAIT_HD);
        jdbc.update("UPDATE forfait SET prix = 12.50 WHERE id = ?", FORFAIT_PREMIUM);
        jdbc.update("DELETE FROM tva_types WHERE center_id = ?", CENTRE_A);
        tva(CENTRE_A, "19.00");
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");
        seance("AMRANI_12", P_AMRANI, CENTRE_A, "2026-01-12", "VALIDEE");
        seance("AMRANI_19", P_AMRANI, CENTRE_A, "2026-01-19", "VALIDEE");
        seance("BELKACEM_06", P_BELKACEM, CENTRE_A, "2026-01-06", "VALIDEE");
        seance("CHIKHI_07", P_CHIKHI, CENTRE_A, "2026-01-07", "VALIDEE");

        golden("arrondis_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("arrondis_etat", CENTRE_A);
    }

    @Test
    void sans_type_de_tva_actif_la_facture_est_emise_a_zero_pour_cent() throws Exception {
        jdbc.update("DELETE FROM tva_types WHERE center_id = ?", CENTRE_A);
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");

        golden("sans_tva_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("sans_tva_etat", CENTRE_A);
    }

    @Test
    void le_taux_de_tva_est_celui_du_jour_de_la_validation_et_non_celui_de_la_fin_de_periode() throws Exception {
        // Nouveau code F3 (KB-FAC-22 corrigé) : la facture fige la TVA en vigueur à la fin de la période facturée (19 %), pas celle du jour (9 %).
        // KB-FAC-22 : 19 % jusqu'au 2026-03-31 (fin de la période facturée : janvier), 9 % ensuite (aujourd'hui).
        jdbc.update("DELETE FROM tva_types WHERE center_id = ?", CENTRE_A);
        tva(CENTRE_A, "19.00", "2000-01-01", "2026-03-31");
        tva(CENTRE_A, "9.00", "2026-04-01", null);
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");

        golden("tva_du_jour_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("tva_du_jour_etat_nouveau_code", CENTRE_A);
    }

    @Test
    void un_type_de_tva_inactif_ou_echu_n_est_pas_retenu() throws Exception {
        jdbc.update("DELETE FROM tva_types WHERE center_id = ?", CENTRE_A);
        tva(CENTRE_A, "19.00", "2000-01-01", "2020-12-31");
        jdbc.update("UPDATE tva_types SET actif = FALSE WHERE center_id = ?", CENTRE_A);
        tva(CENTRE_A, "7.00", "2000-01-01", "2020-12-31");
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");

        golden("tva_inactive_ou_echue_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("tva_inactive_ou_echue_etat", CENTRE_A);
    }

    @Test
    void un_patient_sans_prise_en_charge_est_facture_a_zero_sur_un_forfait_non_defini() throws Exception {
        // Nouveau code F3 (KB-FAC-32) : la validation est refusée (422) tant qu'une séance éligible n'a pas de forfait ; rien n'est émis.
        jdbc.update("DELETE FROM prise_en_charge WHERE patient_id = ?", P_CHIKHI);
        seance("CHIKHI_07", P_CHIKHI, CENTRE_A, "2026-01-07", "VALIDEE");
        seance("CHIKHI_14", P_CHIKHI, CENTRE_A, "2026-01-14", "VALIDEE");

        golden("forfait_non_defini_200_nouveau_code", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("forfait_non_defini_etat_nouveau_code", CENTRE_A);
        goldenEcritures("forfait_non_defini_ecritures_nouveau_code", CENTRE_A);
    }

    @Test
    void les_prix_et_libelles_sont_figes_apres_l_emission() throws Exception {
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");
        valider(mois(CENTRE_A, JANVIER, true));

        jdbc.update("UPDATE forfait SET prix = 9999.00, libelle = 'Renomme' WHERE id = ?", FORFAIT_HD);
        jdbc.update("UPDATE patients SET nom = 'Renomme', numero_assurance = 'AUTRE' WHERE id = ?", P_AMRANI);

        goldenEtat("figes_apres_changement_etat", CENTRE_A);
    }
}
