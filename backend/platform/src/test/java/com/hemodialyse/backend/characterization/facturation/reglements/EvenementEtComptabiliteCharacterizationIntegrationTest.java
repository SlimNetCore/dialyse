package com.hemodialyse.backend.characterization.facturation.reglements;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * F4 : événement {@code FactureReglementRecordedEvent} réellement publié par l'enregistrement d'un règlement, et
 * écritures de règlement produites par la Comptabilité (écouteur {@code ReglementEncaisseComptabiliteListener}, après
 * commit, transaction propre). La Comptabilité lit aussi {@code facture_reglements} (reconstruction).
 */
class EvenementEtComptabiliteCharacterizationIntegrationTest extends ReglementsCharacterizationSupport {

    @Override
    protected String theme() {
        return "evenement";
    }

    private void periode(int annee, int mois, boolean cloturee) {
        jdbc.update("INSERT INTO periodes_comptables (id, annee, center_id, cloturee, cloturee_at, cloturee_by, mois) "
                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, 'compta', ?)", UUID.randomUUID(), annee, CENTRE_A, cloturee, mois);
    }

    @Test
    void un_reglement_publie_un_evenement_avec_le_montant_saisi_la_date_et_l_auteur() throws Exception {
        UUID facture = factureMille();

        enregistrer(facture, corps(CENTRE_A, "400.00", "2026-02-10", "caissier-1"));

        // Nouveau code F4 : auteur de l'événement = utilisateur de la session.
        goldenEvenements("nominal_evenements_nouveau_code");
    }

    @Test
    void l_evenement_porte_les_valeurs_par_defaut_jour_d_execution_et_auteur_system() throws Exception {
        UUID facture = factureMille();

        enregistrer(facture, corps(CENTRE_A, "100.00", null, null));

        // Nouveau code F4 : l'auteur de l'événement est l'utilisateur de la session, non plus « system ».
        goldenEvenements("defauts_evenements_nouveau_code");
    }

    @Test
    void l_evenement_porte_le_montant_non_arrondi_et_l_auteur_nettoye_alors_que_la_base_garde_le_montant_arrondi() throws Exception {
        // KB-FAC-46 : l'événement reçoit le montant du corps (500.005), la base 500.01.
        UUID facture = factureMille();

        enregistrer(facture, corps(CENTRE_A, "500.005", "2026-02-10", "  caissier  "));

        // Nouveau code F4 : l'auteur de l'événement est l'utilisateur de la session (le montant non arrondi est reproduit).
        goldenEvenements("montant_non_arrondi_evenements_nouveau_code");
        goldenReglements("montant_non_arrondi_base_nouveau_code", CENTRE_A); // Nouveau code F4 : auteur = utilisateur de la session
        goldenEcritures("montant_non_arrondi_ecritures", CENTRE_A);
    }

    @Test
    void un_remboursement_publie_un_evenement_au_montant_negatif() throws Exception {
        UUID facture = factureMille();
        enregistrer(facture, corps(CENTRE_A, "600.00", "2026-02-10", "u"));

        enregistrer(facture, corps(CENTRE_A, "-150.00", "2026-02-11", "u"));

        // Nouveau code F4 : auteur de l'événement = utilisateur de la session.
        goldenEvenements("remboursement_evenements_nouveau_code");
    }

    @Test
    void un_refus_ne_publie_aucun_evenement_et_ne_produit_aucune_ecriture() throws Exception {
        UUID facture = factureMille();

        enregistrer(UUID.randomUUID(), corps(CENTRE_A, "100.00", "2026-02-10", "u"));
        enregistrer(facture, corps(CENTRE_A, "0", "2026-02-10", "u"));

        assertEquals(0, evenements.recus().size());
        assertEquals(0, compter("ecritures_comptables", CENTRE_A));
    }

    @Test
    void la_comptabilite_ecrit_un_reglement_en_banque_a_la_date_du_reglement() throws Exception {
        UUID facture = factureMille();
        enregistrer(facture, corps(CENTRE_A, "400.00", "2026-02-10", "caissier-1"));

        goldenEcritures("nominal_ecritures", CENTRE_A);
    }

    @Test
    void sans_ecriture_de_vente_le_compte_client_est_celui_des_patients_meme_si_le_payeur_a_un_compte() throws Exception {
        UUID facture = factureMille();
        jdbc.update("INSERT INTO comptes_payeurs (center_id, payeur_id, compte) VALUES (?, ?, '411210')", CENTRE_A, PAYEUR_1);
        enregistrer(facture, corps(CENTRE_A, "400.00", "2026-02-10", "caissier-1"));

        goldenEcritures("compte_payeur_sans_vente_ecritures", CENTRE_A);
    }

    @Test
    void avec_une_ecriture_de_vente_le_reglement_credite_le_compte_client_debite_par_la_vente() throws Exception {
        UUID facture = factureMille();
        jdbc.update("INSERT INTO comptes_payeurs (center_id, payeur_id, compte) VALUES (?, ?, '411210')", CENTRE_A, PAYEUR_1);
        doPost2("/api/v1/comptabilite/rebuild", json("{'centerId':'" + CENTRE_A + "','from':'2026-01-01','to':'2026-01-31'}"));
        assertEquals(1, compter("ecritures_comptables", CENTRE_A));

        enregistrer(facture, corps(CENTRE_A, "400.00", "2026-02-10", "caissier-1"));

        goldenEcritures("compte_payeur_avec_vente_ecritures", CENTRE_A);
    }

    @Test
    void un_remboursement_est_ecrit_en_valeur_absolue_comme_un_reglement() throws Exception {
        // Comptabilité C5 (B6) : la valeur absolue est écrite ; le sens de l'écriture ne tient pas compte du signe.
        UUID facture = factureMille();
        enregistrer(facture, corps(CENTRE_A, "600.00", "2026-02-10", "u"));
        enregistrer(facture, corps(CENTRE_A, "-150.00", "2026-02-11", "u"));

        // Nouveau code F4 (KB-FAC-47 corrigé) : un remboursement est une écriture de sens inverse (débit du compte client,
        // crédit de la banque), non plus un encaissement en valeur absolue.
        goldenEcritures("remboursement_ecritures_nouveau_code", CENTRE_A);
    }

    @Test
    void deux_reglements_identiques_du_meme_jour_par_le_meme_auteur_donnent_chacun_leur_ecriture() throws Exception {
        // Deux encaissements, deux écritures (deux pièces, deux sources distinctes) : pas d'écrasement par l'identifiant
        // retrouvé (facture, centre, date, montant, auteur).
        UUID facture = factureMille();
        String corps = corps(CENTRE_A, "250.00", "2026-02-10", "caissier");

        enregistrer(facture, corps);
        enregistrer(facture, corps);

        assertEquals(2, compter("facture_reglements", CENTRE_A));
        goldenEcritures("doublon_ecritures", CENTRE_A);
    }

    @Test
    void deux_reglements_de_montants_ou_d_auteurs_differents_donnent_chacun_leur_ecriture() throws Exception {
        UUID facture = factureMille();

        enregistrer(facture, corps(CENTRE_A, "250.00", "2026-02-10", "caissier"));
        enregistrer(facture, corps(CENTRE_A, "250.00", "2026-02-10", "autre-caissier"));
        enregistrer(facture, corps(CENTRE_A, "100.00", "2026-02-10", "caissier"));

        assertEquals(3, compter("ecritures_comptables", CENTRE_A));
    }

    @Test
    void un_mois_comptable_cloture_n_empeche_pas_le_reglement_mais_aucune_ecriture_n_est_produite() throws Exception {
        UUID facture = factureMille();
        periode(2026, 2, true);

        golden("mois_cloture_200", enregistrer(facture, corps(CENTRE_A, "400.00", "2026-02-10", "u")));

        assertEquals(1, compter("facture_reglements", CENTRE_A));
        assertEquals(0, compter("ecritures_comptables", CENTRE_A));
        // Nouveau code F4 : auteur de l'événement = utilisateur de la session.
        goldenEvenements("mois_cloture_evenements_nouveau_code");
    }

    @Test
    void la_cloture_porte_sur_la_date_du_reglement_et_non_sur_celle_de_la_facture_ni_du_jour() throws Exception {
        UUID facture = factureMille();
        periode(2026, 1, true);

        enregistrer(facture, corps(CENTRE_A, "100.00", "2026-02-10", "u"));
        enregistrer(facture, corps(CENTRE_A, "100.00", "2026-01-20", "u"));

        assertEquals(2, compter("facture_reglements", CENTRE_A));
        assertEquals(1, compter("ecritures_comptables", CENTRE_A));
        goldenEcritures("cloture_janvier_ecritures", CENTRE_A);
    }

    @Test
    void un_mois_ouvert_explicitement_laisse_passer_l_ecriture() throws Exception {
        UUID facture = factureMille();
        periode(2026, 2, false);

        enregistrer(facture, corps(CENTRE_A, "400.00", "2026-02-10", "u"));

        assertEquals(1, compter("ecritures_comptables", CENTRE_A));
    }

    @Test
    void la_reconstruction_de_la_comptabilite_rattrape_un_reglement_enregistre_en_mois_cloture() throws Exception {
        UUID facture = factureMille();
        periode(2026, 2, true);
        enregistrer(facture, corps(CENTRE_A, "400.00", "2026-02-10", "u"));
        jdbc.update("DELETE FROM periodes_comptables WHERE center_id = ?", CENTRE_A);

        golden("rebuild_200", doPost2("/api/v1/comptabilite/rebuild",
                json("{'centerId':'" + CENTRE_A + "','from':'2026-02-01','to':'2026-02-28'}")));

        goldenEcritures("rebuild_ecritures", CENTRE_A);
    }

    private Resp doPost2(String chemin, String corps) throws Exception {
        var r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(java.net.URI.create(chemin))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(corps).with(adminA())).andReturn().getResponse();
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8), r.getContentAsByteArray(), null);
    }

    @Test
    void un_reglement_pose_en_base_sans_passer_par_la_route_ne_publie_pas_d_evenement() throws Exception {
        // Les règlements de reprise (migration, solde d'ouverture) écrivent directement la table : aucun événement.
        UUID facture = factureMille();
        paiement(facture, CENTRE_A, "100.00", "2026-02-10", "migration", "REG-SEED-001", "2026-02-10 09:00:00");

        assertEquals(0, evenements.recus().size());
        assertEquals(0, compter("ecritures_comptables", CENTRE_A));
    }
}
