package com.hemodialyse.backend.characterization.facturation.validation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation F3 — séances retenues, ignorées et passées à l'état facturé par la validation ; période
 * déjà facturée, idempotence, seconde validation (RG-FAC-020, RG-FAC-024, RG-FAC-025).
 */
class SeancesFactureesCharacterizationIntegrationTest extends ValidationCharacterizationSupport {

    @Override
    protected String theme() {
        return "seances";
    }

    @Test
    void seules_les_seances_validees_ou_signees_sans_facture_de_la_periode_et_du_centre_sont_facturees() throws Exception {
        seance("VALIDEE_02", P_AMRANI, CENTRE_A, "2026-01-02", "VALIDEE");
        seance("SIGNEE_03", P_AMRANI, CENTRE_A, "2026-01-03", "SIGNEE");
        seance("BROUILLON_04", P_AMRANI, CENTRE_A, "2026-01-04", "BROUILLON");
        seance("ABSENT_05", P_AMRANI, CENTRE_A, "2026-01-05", "ABSENT");
        seance("FACTUREE_06", P_AMRANI, CENTRE_A, "2026-01-06", "FACTUREE");
        seance("VALIDEE_minuscule_07", P_AMRANI, CENTRE_A, "2026-01-07", "validee");
        UUID dejaLiee = seance("VALIDEE_LIEE_08", P_AMRANI, CENTRE_A, "2026-01-08", "VALIDEE");
        UUID autreFacture = factureExistante(CENTRE_A, P_AMRANI, "ANCIENNE-0001", "2026-01-01", "2026-01-07");
        jdbc.update("UPDATE seances SET facture_id = ? WHERE id = ?", autreFacture, dejaLiee);
        seance("VEILLE_31_12", P_AMRANI, CENTRE_A, "2025-12-31", "VALIDEE");
        seance("BORNE_01_01", P_AMRANI, CENTRE_A, "2026-01-01", "VALIDEE");
        seance("BORNE_31_01", P_AMRANI, CENTRE_A, "2026-01-31", "VALIDEE");
        seance("LENDEMAIN_01_02", P_AMRANI, CENTRE_A, "2026-02-01", "VALIDEE");
        seance("CENTRE_B", P_CENTRE_B, CENTRE_B, "2026-01-02", "VALIDEE");

        golden("eligibilite_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("eligibilite_etat", CENTRE_A);
        goldenEtat("eligibilite_centre_B_intact", CENTRE_B);
    }

    @Test
    void la_periode_peut_etre_donnee_par_deux_dates_bornes_comprises() throws Exception {
        seance("DEBUT_10", P_AMRANI, CENTRE_A, "2026-01-10", "VALIDEE");
        seance("MILIEU_15", P_AMRANI, CENTRE_A, "2026-01-15", "VALIDEE");
        seance("FIN_20", P_AMRANI, CENTRE_A, "2026-01-20", "VALIDEE");
        seance("HORS_21", P_AMRANI, CENTRE_A, "2026-01-21", "VALIDEE");

        golden("periode_par_dates_200", valider(dates(CENTRE_A, "2026-01-10", "2026-01-20", true)));
        goldenEtat("periode_par_dates_etat", CENTRE_A);
    }

    @Test
    void le_mois_l_emporte_sur_les_deux_dates_pour_choisir_les_seances_et_la_periode_de_la_facture() throws Exception {
        seance("JANVIER_10", P_AMRANI, CENTRE_A, "2026-01-10", "VALIDEE");
        seance("MARS_10", P_AMRANI, CENTRE_A, "2026-03-10", "VALIDEE");

        golden("mois_prioritaire_200", valider(json("{'centerId':'" + CENTRE_A + "','userId':'u1','month':'2026-01',"
                + "'periodStart':'2026-03-01','periodEnd':'2026-03-31','regroupementMultiForfait':true}")));
        goldenEtat("mois_prioritaire_etat", CENTRE_A);
    }

    @Test
    void sans_aucune_seance_a_facturer_rien_n_est_cree_et_la_sequence_n_est_pas_touchee() throws Exception {
        seance("ABSENT_05", P_AMRANI, CENTRE_A, "2026-01-05", "ABSENT");

        golden("aucune_seance_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("aucune_seance_etat", CENTRE_A);
    }

    @Test
    void revalider_la_meme_periode_ne_cree_rien_de_plus() throws Exception {
        // Nouveau code F3 (KB-FAC-29 corrigé) : la revalidation n'émet rien et ne publie aucun événement.
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");
        seance("BELKACEM_06", P_BELKACEM, CENTRE_A, "2026-01-06", "VALIDEE");

        golden("premiere_validation_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("apres_premiere_etat", CENTRE_A);
        golden("seconde_validation_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("apres_seconde_identique_etat", CENTRE_A);
        goldenEvenements("evenements_des_deux_validations_nouveau_code");
        goldenEcritures("ecritures_sans_doublon", CENTRE_A);
    }

    @Test
    void une_seance_validee_apres_l_emission_donne_une_seconde_facture_du_meme_patient_pour_la_meme_periode() throws Exception {
        // KB-FAC-26 : aucun contrôle « période déjà facturée » sur les factures ; seul l'état des séances protège.
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");
        valider(mois(CENTRE_A, JANVIER, true));
        seance("AMRANI_28_TARDIVE", P_AMRANI, CENTRE_A, "2026-01-28", "VALIDEE");

        golden("seance_tardive_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("seance_tardive_etat", CENTRE_A);
        goldenEcritures("seance_tardive_ecritures", CENTRE_A);
    }

    @Test
    void une_facture_deja_emise_sur_la_periode_n_empeche_pas_d_emettre_pour_les_seances_restantes() throws Exception {
        factureExistante(CENTRE_A, P_AMRANI, "ANCIENNE-0001", "2026-01-01", "2026-01-31");
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");

        golden("periode_deja_facturee_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("periode_deja_facturee_etat", CENTRE_A);
    }

    @Test
    void une_seance_d_un_patient_d_un_autre_centre_n_est_pas_facturee_par_le_centre_demande() throws Exception {
        seance("ZERROUKI_05", P_CENTRE_B, CENTRE_B, "2026-01-05", "VALIDEE");
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");

        valider(mois(CENTRE_A, JANVIER, true));

        assertEquals(1, compter("factures", CENTRE_A));
        assertEquals(0, compter("factures", CENTRE_B));
        goldenEtat("centre_B_non_facture", CENTRE_B);
    }
}
