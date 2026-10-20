package com.hemodialyse.backend.characterization.facturation.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation F3 — numérotation des factures émises par {@code POST /facturation/validate} : séquence
 * annuelle du centre, modèle de numéro, doublons (contrainte d'unicité), réservation par la validation seulement.
 */
class NumerotationDesFacturesCharacterizationIntegrationTest extends ValidationCharacterizationSupport {

    @Override
    protected String theme() {
        return "numerotation";
    }

    private void troisPatients(String mois) {
        seance("AMRANI_05", P_AMRANI, CENTRE_A, mois + "-05", "VALIDEE");
        seance("BELKACEM_06", P_BELKACEM, CENTRE_A, mois + "-06", "VALIDEE");
        seance("CHIKHI_07", P_CHIKHI, CENTRE_A, mois + "-07", "VALIDEE");
    }

    @Test
    void les_factures_prennent_la_sequence_courante_plus_un_dans_l_ordre_alphabetique_des_patients() throws Exception {
        troisPatients("2026-01");

        golden("sequence_courante_plus_un_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("sequence_courante_plus_un_etat", CENTRE_A);
    }

    @Test
    void sans_ligne_de_sequence_pour_l_annee_la_premiere_facture_porte_le_numero_un_et_la_ligne_est_creee() throws Exception {
        jdbc.update("DELETE FROM facture_sequence WHERE center_id = ?", CENTRE_A);
        troisPatients("2026-01");

        golden("premiere_facture_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("premiere_facture_etat", CENTRE_A);
    }

    @Test
    void la_sequence_est_celle_de_l_annee_du_jour_de_validation_et_non_celle_de_la_periode_facturee() throws Exception {
        // KB-FAC-23 : une période de 2025 reçoit un numéro de l'année en cours ; la séquence de 2025 n'est pas touchée.
        sequence(CENTRE_A, anneeCourante() - 1, 99);
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2025-11-05", "VALIDEE");
        seance("BELKACEM_06", P_BELKACEM, CENTRE_A, "2025-11-06", "VALIDEE");

        golden("annee_du_jour_200", valider(mois(CENTRE_A, "2025-11", true)));
        goldenEtat("annee_du_jour_etat", CENTRE_A);
    }

    @Test
    void le_modele_du_centre_formate_annee_centre_et_largeur_de_sequence() throws Exception {
        parametres(CENTRE_A, "{CENTER}/F{SEQ6}");
        troisPatients("2026-01");

        golden("modele_centre_largeur_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("modele_centre_largeur_etat", CENTRE_A);
    }

    @Test
    void le_modele_peut_porter_l_annee_sur_deux_chiffres() throws Exception {
        parametres(CENTRE_A, "F{YY}-{SEQ}");
        nommerTexte(String.format("F%02d-", anneeCourante() % 100), "F<AA>-");
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");

        golden("modele_annee_deux_chiffres_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("modele_annee_deux_chiffres_etat", CENTRE_A);
    }

    @Test
    void sans_parametres_du_centre_le_modele_par_defaut_s_applique() throws Exception {
        jdbc.update("DELETE FROM facturation_settings WHERE center_id = ?", CENTRE_A);
        troisPatients("2026-01");

        golden("modele_par_defaut_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("modele_par_defaut_etat", CENTRE_A);
    }

    @Test
    void la_simulation_ne_reserve_pas_de_numero_et_annonce_les_numeros_que_la_validation_attribue() throws Exception {
        troisPatients("2026-01");

        Resp apercu = doPost("/preview", mois(CENTRE_A, JANVIER, true), adminA());
        assertEquals(200, apercu.status());
        goldenNumerosApercu("apercu_numeros_prevus", apercu);
        goldenEtat("apercu_ne_reserve_rien_etat", CENTRE_A);

        valider(mois(CENTRE_A, JANVIER, true));
        goldenEtat("apres_validation_etat", CENTRE_A);
    }

    @Test
    void deux_validations_successives_continuent_la_sequence() throws Exception {
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");
        seance("BELKACEM_06", P_BELKACEM, CENTRE_A, "2026-01-06", "VALIDEE");
        valider(mois(CENTRE_A, JANVIER, true));
        seance("AMRANI_FEV_05", P_AMRANI, CENTRE_A, "2026-02-05", "VALIDEE");
        valider(mois(CENTRE_A, "2026-02", true));

        goldenEtat("deux_validations_etat", CENTRE_A);
    }

    @Test
    void la_sequence_est_propre_a_chaque_centre() throws Exception {
        parametres(CENTRE_B, "B-{SEQ}");
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");
        seance("ZERROUKI_05", P_CENTRE_B, CENTRE_B, "2026-01-05", "VALIDEE");
        valider(mois(CENTRE_A, JANVIER, true));
        doPost("/validate", mois(CENTRE_B, JANVIER, true), as(CENTRE_B, "ADMIN"));

        goldenEtat("centre_A_etat", CENTRE_A);
        goldenEtat("centre_B_etat", CENTRE_B);
    }

    @Test
    void un_numero_deja_pris_par_une_facture_existante_fait_echouer_toute_la_validation_et_restitue_la_sequence() throws Exception {
        // Nouveau code F3 (KB-FAC-24/25) : 409 explicite au lieu du 500, même retour arrière complet.
        // KB-FAC-24 : la séquence peut être en retard sur les factures ; la contrainte d'unicité (centre, numéro) refuse
        // le doublon, la transaction est annulée (aucune facture, séances intactes, séquence restituée), et chaque nouvelle
        // tentative échoue de la même façon tant que la séquence n'est pas corrigée à la main.
        factureExistante(CENTRE_A, P_CHIKHI, "FAC-" + anneeCourante() + "-0043", "2025-12-01", "2025-12-31");
        troisPatients("2026-01");

        golden("doublon_500_nouveau_code", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("doublon_etat", CENTRE_A);
        golden("doublon_nouvelle_tentative_500_nouveau_code", valider(mois(CENTRE_A, JANVIER, true)));
        assertEquals(0, evenements.recus().size());
        goldenEcritures("doublon_ecritures", CENTRE_A);
    }

    @Test
    void un_modele_sans_numero_de_sequence_donne_le_meme_numero_a_deux_factures_et_la_validation_echoue() throws Exception {
        // Nouveau code F3 (KB-FAC-24/25) : 409 explicite ; la saisie d'un tel modèle est refusée par les réglages (F1, 400).
        // KB-FAC-25 : aucun contrôle du modèle à la saisie des réglages ; le refus ne vient que de la base.
        parametres(CENTRE_A, "FACTURE-UNIQUE");
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");
        seance("BELKACEM_06", P_BELKACEM, CENTRE_A, "2026-01-06", "VALIDEE");

        golden("modele_sans_sequence_deux_factures_500_nouveau_code", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("modele_sans_sequence_etat", CENTRE_A);
    }

    @Test
    void un_modele_sans_numero_de_sequence_suffit_pour_une_seule_facture() throws Exception {
        // Nouveau code F3 : une 2e validation avec ce modèle (posé en base, hors saisie) répond 409.
        parametres(CENTRE_A, "FACTURE-UNIQUE");
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");

        golden("modele_sans_sequence_une_facture_200", valider(mois(CENTRE_A, JANVIER, true)));
        goldenEtat("modele_sans_sequence_une_facture_etat", CENTRE_A);
        golden("modele_sans_sequence_seconde_validation_500_nouveau_code", validerUnAutrePatient());
    }

    private Resp validerUnAutrePatient() throws Exception {
        seance("BELKACEM_06", P_BELKACEM, CENTRE_A, "2026-01-06", "VALIDEE");
        return valider(mois(CENTRE_A, JANVIER, true));
    }

    /**
     * Seuls les numéros prévus (et non les identifiants de séance) sont comparés : ce sont ceux que la validation attribue.
     */
    private void goldenNumerosApercu(String name, Resp apercu) {
        var numeros = mapper.createArrayNode();
        json(apercu).path("invoices").forEach(i -> numeros.add(i.path("numeroFacture").asString("?")));
        goldenNode(name, numeros);
    }
}
