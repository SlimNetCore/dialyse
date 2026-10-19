package com.hemodialyse.backend.characterization.comptabilite.ecritures;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation C3 — numéro de pièce (RG-CPT-010, B1/B2) : {@code <JOURNAL>-<année>-<n° sur 6 chiffres>},
 * plus grand numéro existant du centre, du journal et de l'année de la date d'écriture, plus un, sans verrou ni
 * contrainte d'unicité en base. Observé par les générateurs de production (écritures de ventes et de règlements, C5)
 * et par des écritures de fixture qui fixent l'état de départ. La course entre deux numérotations simultanées n'est
 * pas déclenchée (non déterministe sans seam) : seule l'absence de contrainte en base est constatée.
 */
class NumerotationDesPiecesCharacterizationIntegrationTest extends EcrituresCharacterizationSupport {

    @Override
    protected String theme() {
        return "numerotation";
    }

    // ------------------------------------------------------------------ séquence

    @Test
    void les_pieces_d_un_journal_sont_numerotees_a_la_suite_a_partir_de_000001_avec_l_annee_de_la_date_d_ecriture() throws Exception {
        genererVente(1, "100.00", "0.00", "100.00", "2026-03-05");
        genererVente(2, "100.00", "0.00", "100.00", "2026-03-20");
        genererVente(3, "100.00", "0.00", "100.00", "2026-04-02");
        goldenEcritures("sequence_trois_ventes_2026", CENTRE_A);
    }

    @Test
    void chaque_annee_repart_a_000001_et_une_date_ancienne_prend_la_suite_de_son_annee() throws Exception {
        genererVente(1, "100.00", "0.00", "100.00", "2025-12-31");
        genererVente(2, "100.00", "0.00", "100.00", "2026-01-01");
        genererVente(3, "100.00", "0.00", "100.00", "2026-01-02");
        genererVente(4, "100.00", "0.00", "100.00", "2025-12-30");
        goldenEcritures("sequence_par_annee_de_la_date_d_ecriture", CENTRE_A);
    }

    @Test
    void chaque_journal_et_chaque_centre_a_sa_propre_sequence() throws Exception {
        genererVente(1, "100.00", "0.00", "100.00", "2026-03-05");
        genererReglement(CENTRE_A, fid(1), rid(1), "50.00", "2026-03-06", "BANQUE", null);
        genererReglement(CENTRE_A, fid(1), rid(2), "20.00", "2026-03-07", "CAISSE", null);
        genererReglement(CENTRE_A, fid(1), rid(3), "30.00", "2026-03-08", "BANQUE", null);
        genererVente(CENTRE_B, fid(11), "FACT-B", "10.00", "0.00", "10.00", "2026-03-05", null);
        goldenEcritures("sequence_par_journal_centre_a", CENTRE_A);
        goldenEcritures("sequence_par_journal_centre_b", CENTRE_B);
    }

    // ------------------------------------------------------------------ plus grand + 1

    @Test
    void le_numero_suivant_est_le_plus_grand_existant_plus_un_meme_avec_des_trous() throws Exception {
        vente(1, "VE-2026-000003", "2026-03-01", "VALIDEE", "10.00");
        vente(2, "VE-2026-000010", "2026-03-02", "VALIDEE", "10.00");
        genererVente(1, "100.00", "0.00", "100.00", "2026-03-05");
        genererVente(2, "100.00", "0.00", "100.00", "2026-03-06");
        goldenEcritures("plus_grand_plus_un_avec_trous", CENTRE_A);
    }

    @Test
    void le_numero_d_une_ecriture_supprimee_est_reutilise_malgre_le_commentaire_du_domaine_disant_jamais_reutilise() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-01", "VALIDEE", "10.00");
        vente(2, "VE-2026-000002", "2026-03-02", "VALIDEE", "10.00");
        vente(3, "VE-2026-000003", "2026-03-03", "VALIDEE", "10.00");
        jdbc.update("DELETE FROM lignes_ecriture WHERE ecriture_id = ?", ecr(3));
        jdbc.update("DELETE FROM ecritures_comptables WHERE id = ?", ecr(3));
        genererVente(1, "100.00", "0.00", "100.00", "2026-03-05");
        goldenEcritures("numero_supprime_reutilise", CENTRE_A);
    }

    @Test
    void la_sequence_d_un_journal_ou_d_une_annee_ne_se_melange_pas_avec_celle_d_un_autre() throws Exception {
        ecriture(1, CENTRE_A, "BQ", "BQ-2026-000050", "2026-03-01", "2026-03-01", "VALIDEE", "Banque", null, null,
                L.d("512", "10.00"), L.c("411100", "10.00"));
        vente(2, "VE-2025-000099", "2025-12-01", "VALIDEE", "10.00");
        vente(3, CENTRE_B, "VE", "VE-2026-000077", "2026-03-01", "VALIDEE", "10.00");
        genererVente(1, "100.00", "0.00", "100.00", "2026-03-05");
        goldenEcritures("sequences_independantes_journal_annee_centre", CENTRE_A);
    }

    // ------------------------------------------------------------------ numéros hors norme

    @Test
    void un_numero_dont_la_fin_n_est_pas_numerique_est_ignore_et_la_sequence_continue_sans_doublon() throws Exception {
        vente(1, "VE-2026-ABC", "2026-03-01", "VALIDEE", "10.00");
        genererVente(1, "100.00", "0.00", "100.00", "2026-03-05");
        genererVente(2, "100.00", "0.00", "100.00", "2026-03-06");
        goldenEcritures("suffixe_non_numerique_ignore_nouveau_code", CENTRE_A);
    }

    @Test
    void un_numero_hors_du_motif_du_journal_et_de_l_annee_n_est_pas_vu() throws Exception {
        vente(1, "FACTURE-9", "2026-03-01", "VALIDEE", "10.00");
        genererVente(1, "100.00", "0.00", "100.00", "2026-03-05");
        goldenEcritures("numero_hors_motif_ignore", CENTRE_A);
    }

    @Test
    void passe_999999_le_numero_a_sept_chiffres_continue_sans_doublon_car_le_plus_grand_est_cherche_numeriquement() throws Exception {
        vente(1, "VE-2026-999999", "2026-03-01", "VALIDEE", "10.00");
        genererVente(1, "100.00", "0.00", "100.00", "2026-03-05");
        genererVente(2, "100.00", "0.00", "100.00", "2026-03-06");
        genererVente(3, "100.00", "0.00", "100.00", "2026-03-07");
        goldenEcritures("passage_a_sept_chiffres_sans_doublon_nouveau_code", CENTRE_A);
        // KB-CPT-24 (décision C3/C4, appliquée par C5) : suffixe comparé numériquement, plus de doublon.
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ecritures_comptables WHERE center_id = ? "
                + "AND numero_piece = 'VE-2026-1000000'", Integer.class, CENTRE_A));
    }

    // ------------------------------------------------------------------ pas de contrainte d'unicité (B1, B2)

    @Test
    void la_base_accepte_deux_ecritures_de_meme_numero_et_de_meme_source_aucune_contrainte_d_unicite() throws Exception {
        UUID source = fid(1);
        ecriture(1, CENTRE_A, "VE", "VE-2026-000001", "2026-03-01", "2026-03-01", "VALIDEE", "Première", source, null,
                L.d("411100", "10.00"), L.c("706", "10.00"));
        ecriture(2, CENTRE_A, "VE", "VE-2026-000001", "2026-03-01", "2026-03-01", "VALIDEE", "Doublon", source, null,
                L.d("411100", "10.00"), L.c("706", "10.00"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ecritures_comptables WHERE center_id = ? "
                + "AND numero_piece = 'VE-2026-000001' AND source_id = ?", Integer.class, CENTRE_A, source));
        goldenEcritures("doublons_de_numero_et_de_source_acceptes_par_la_base", CENTRE_A);
    }

    @Test
    void la_generation_d_une_source_deja_comptabilisee_en_double_ne_cree_rien_de_plus() throws Exception {
        UUID source = fid(1);
        ecriture(1, CENTRE_A, "VE", "VE-2026-000001", "2026-03-01", "2026-03-01", "VALIDEE", "Première", source, null,
                L.d("411100", "10.00"), L.c("706", "10.00"));
        ecriture(2, CENTRE_A, "VE", "VE-2026-000001", "2026-03-01", "2026-03-01", "VALIDEE", "Doublon", source, null,
                L.d("411100", "10.00"), L.c("706", "10.00"));
        genererVente(CENTRE_A, source, "FACT-1", "10.00", "0.00", "10.00", "2026-03-01", null);
        assertEquals(2, count("ecritures_comptables", CENTRE_A));
    }

    // ------------------------------------------------------------------ idempotence par source (R6)

    @Test
    void une_facture_n_est_comptabilisee_qu_une_fois_meme_si_le_journal_des_ventes_a_change_depuis() throws Exception {
        genererVente(1, "100.00", "0.00", "100.00", "2026-03-05");
        assertEquals(200, doPut("/journaux/OD", json("{'libelle':'Opérations diverses','actif':true}"), adminA()).status());
        assertEquals(200, doPut("/mapping", json("{'compteVentes':'706','compteClientPatient':'411100',"
                + "'compteClientDefaut':'411500','compteBanque':'512','compteCaisse':'530','compteTVACollectee':'44571',"
                + "'journaux':{'VENTE':'OD'}}"), adminA()).status());
        genererVente(1, "100.00", "0.00", "100.00", "2026-03-05");
        genererVente(2, "100.00", "0.00", "100.00", "2026-03-06");
        goldenEcritures("idempotence_par_source_quel_que_soit_le_journal", CENTRE_A);
    }

    @Test
    void une_facture_deja_comptabilisee_rend_l_ecriture_existante_sans_regarder_les_nouveaux_montants() throws Exception {
        var premiere = genererVente(1, "100.00", "0.00", "100.00", "2026-03-05");
        var seconde = genererVente(1, "999.00", "0.00", "999.00", "2026-03-06");
        assertEquals(premiere.getId(), seconde.getId());
        assertEquals(1, count("ecritures_comptables", CENTRE_A));
        goldenLignes("idempotence_montants_inchanges", CENTRE_A);
    }

    @Test
    void un_reglement_d_identifiant_connu_n_est_comptabilise_qu_une_fois_et_une_facture_et_un_reglement_de_meme_id_se_confondent() throws Exception {
        genererReglement(CENTRE_A, fid(1), rid(1), "50.00", "2026-03-06", "BANQUE", null);
        genererReglement(CENTRE_A, fid(1), rid(1), "70.00", "2026-03-07", "CAISSE", null);
        goldenEcritures("idempotence_reglement_par_identifiant", CENTRE_A);
        genererVente(CENTRE_A, rid(1), "FACT-MEME-ID", "10.00", "0.00", "10.00", "2026-03-08", null);
        assertEquals(1, count("ecritures_comptables", CENTRE_A));
    }

    // ------------------------------------------------------------------ refus et numéro

    @Test
    void un_refus_pour_periode_close_ne_consomme_pas_de_numero() throws Exception {
        assertEquals(200, doPost("/periodes/cloturer", json("{'annee':2026,'mois':3,'userId':'u'}"), adminA()).status());
        assertEquals("PERIODE_CLOTUREE", org.junit.jupiter.api.Assertions.assertThrows(
                com.hemodialyse.backend.domain.shared.exception.BusinessException.class,
                () -> genererVente(1, "100.00", "0.00", "100.00", "2026-03-05")).getCode());
        genererVente(2, "100.00", "0.00", "100.00", "2026-04-05");
        goldenEcritures("refus_periode_close_sans_numero_consomme", CENTRE_A);
    }

    @Test
    void un_refus_pour_desequilibre_ne_consomme_pas_de_numero() throws Exception {
        assertEquals("ECRITURE_DESEQUILIBREE", org.junit.jupiter.api.Assertions.assertThrows(
                com.hemodialyse.backend.domain.shared.exception.BusinessException.class,
                () -> genererVente(1, "100.00", "19.00", "118.99", "2026-03-05")).getCode());
        genererVente(2, "100.00", "0.00", "100.00", "2026-03-06");
        goldenEcritures("refus_desequilibre_sans_numero_consomme", CENTRE_A);
    }

    // ------------------------------------------------------------------ pièces saisies et extournes partagent la séquence

    @Test
    void une_piece_saisie_dans_le_journal_des_ventes_prend_la_suite_des_ventes_generees() throws Exception {
        genererVente(1, "100.00", "0.00", "100.00", "2026-03-05");
        assertEquals(201, doPost("/modeles", json("{'code':'VENTE-MANUELLE','libelle':'Vente manuelle','journal':'VE','actif':true,"
                + "'lignes':[{'sens':'DEBIT','compte':'512'},{'sens':'CREDIT','compte':'706'}]}"), adminA()).status());
        UUID modele = jdbc.queryForObject("SELECT id FROM modeles_piece WHERE center_id = ?", UUID.class, CENTRE_A);
        assertEquals(201, doPost("/pieces", json("{'modeleId':'" + modele + "','date':'2026-03-10','montants':[5,5]}"),
                adminA()).status());
        genererVente(2, "100.00", "0.00", "100.00", "2026-03-11");
        goldenEcritures("piece_saisie_partage_la_sequence_du_journal", CENTRE_A);
    }
}
