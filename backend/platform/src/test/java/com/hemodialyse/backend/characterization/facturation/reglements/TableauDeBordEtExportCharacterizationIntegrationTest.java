package com.hemodialyse.backend.characterization.facturation.reglements;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F4 : {@code GET /api/v1/reglements/dashboard} (agrégats par situation, période, filtres) et
 * {@code GET /api/v1/reglements/export} (CSV et Excel).
 */
class TableauDeBordEtExportCharacterizationIntegrationTest extends ReglementsCharacterizationSupport {

    @Override
    protected String theme() {
        return "tableau-de-bord-export";
    }

    private String q(String suite) {
        return "?centerId=" + CENTRE_A + "&" + suite;
    }

    /**
     * Cinq factures de février 2026 couvrant toutes les situations, une de janvier, une de décembre 2025, une du
     * centre B.
     */
    private void jeuDeSituations() {
        UUID nonReglee = facture(CENTRE_A, P_AMRANI, "FAC-0001", "2026-02-03", "1000.00", PAYEUR_1, AGENCE_1);
        UUID partielle = facture(CENTRE_A, P_BELKACEM, "FAC-0002", "2026-02-10", "500.00", PAYEUR_2, AGENCE_2);
        UUID reglee = facture(CENTRE_A, P_CHIKHI, "FAC-0003", "2026-02-15", "800.00", null, null);
        UUID tropPercu = facture(CENTRE_A, P_AMRANI, "FAC-0004", "2026-02-20", "300.00", PAYEUR_1, AGENCE_1);
        UUID reglee2 = facture(CENTRE_A, P_BELKACEM, "FAC-0005", "2026-02-28", "200.00", PAYEUR_2, AGENCE_2);
        UUID janvier = facture(CENTRE_A, P_AMRANI, "FAC-0006", "2026-01-31", "100.00", PAYEUR_1, AGENCE_1);
        UUID decembre = facture(CENTRE_A, P_AMRANI, "FAC-0000", "2025-12-31", "50.00", PAYEUR_1, AGENCE_1);
        UUID autreCentre = facture(CENTRE_B, P_CENTRE_B, "FAC-B-0001", "2026-02-03", "9999.00", PAYEUR_B, AGENCE_B);
        paiement(partielle, CENTRE_A, "200.00", "2026-03-01", "u", "REG-SEED-001", "2026-03-01 09:00:00");
        paiement(reglee, CENTRE_A, "800.00", "2026-03-02", "u", "REG-SEED-002", "2026-03-02 09:00:00");
        paiement(tropPercu, CENTRE_A, "350.00", "2026-03-03", "u", "REG-SEED-003", "2026-03-03 09:00:00");
        paiement(reglee2, CENTRE_A, "250.00", "2026-03-04", "u", "REG-SEED-004", "2026-03-04 09:00:00");
        paiement(reglee2, CENTRE_A, "-50.00", "2026-03-05", "u", "REG-SEED-005", "2026-03-05 09:00:00");
        paiement(janvier, CENTRE_A, "100.00", "2026-02-02", "u", "REG-SEED-006", "2026-02-02 09:00:00");
        paiement(decembre, CENTRE_A, "10.00", "2026-01-02", "u", "REG-SEED-007", "2026-01-02 09:00:00");
        paiement(autreCentre, CENTRE_B, "9999.00", "2026-03-06", "u", "REG-SEED-008", "2026-03-06 09:00:00");
    }

    // ------------------------------------------------------------------ tableau de bord

    @Test
    void le_tableau_de_bord_totalise_les_factures_du_mois_par_situation() throws Exception {
        jeuDeSituations();

        golden("fevrier_2026", doGet("/dashboard" + q("year=2026&month=2"), adminA()));
    }

    @Test
    void le_tableau_de_bord_de_l_annee_totalise_janvier_et_fevrier_et_laisse_decembre_a_l_annee_precedente() throws Exception {
        jeuDeSituations();

        golden("annee_2026", doGet("/dashboard" + q("year=2026"), adminA()));
        golden("annee_2025", doGet("/dashboard" + q("year=2025"), adminA()));
    }

    @Test
    void le_total_regle_est_la_somme_signee_des_reglements_des_factures_de_la_periode_quelle_que_soit_leur_date() throws Exception {
        // Les règlements de mars des factures de février comptent dans février : la période filtre la date de facturation.
        jeuDeSituations();

        JsonNode fevrier = json(doGet("/dashboard" + q("year=2026&month=2"), adminA()));

        assertEquals(0, new java.math.BigDecimal("1550.00").compareTo(fevrier.path("totalRegle").decimalValue()));
        assertEquals(0, new java.math.BigDecimal("2800.00").compareTo(fevrier.path("totalFacture").decimalValue()));
        golden("mois_sans_facture", doGet("/dashboard" + q("year=2026&month=5"), adminA()));
    }

    @Test
    void le_tableau_de_bord_filtre_par_caisse_agence_et_centre_payeur() throws Exception {
        jeuDeSituations();

        golden("filtre_caisse_1", doGet("/dashboard" + q("year=2026&month=2&caisseId=" + CAISSE_1), adminA()));
        golden("filtre_agence_2", doGet("/dashboard" + q("year=2026&month=2&agenceId=" + AGENCE_2), adminA()));
        golden("filtre_payeur_1", doGet("/dashboard" + q("year=2026&month=2&centrePayeurId=" + PAYEUR_1), adminA()));
        golden("filtre_inconnu", doGet("/dashboard" + q("year=2026&month=2&caisseId=" + UUID.randomUUID()), adminA()));
    }

    @Test
    void le_tableau_de_bord_refuse_les_periodes_hors_bornes_et_les_parametres_illisibles() throws Exception {
        jeuDeSituations();

        golden("annee_1999", doGet("/dashboard" + q("year=1999"), adminA()));
        golden("annee_3001", doGet("/dashboard" + q("year=3001"), adminA()));
        golden("mois_13", doGet("/dashboard" + q("year=2026&month=13"), adminA()));
        golden("mois_0", doGet("/dashboard" + q("year=2026&month=0"), adminA()));
        golden("sans_annee", doGet("/dashboard" + q(""), adminA()));
        golden("sans_centre", doGet("/dashboard?year=2026", adminA()));
        golden("annee_texte", doGet("/dashboard" + q("year=abc"), adminA()));
    }

    @Test
    void les_bornes_de_periode_du_tableau_de_bord_sont_incluses_et_les_mois_suivants_sont_exclus() throws Exception {
        jeuDeSituations();
        facture(CENTRE_A, P_AMRANI, "FAC-0009", "2026-03-01", "70.00", PAYEUR_1, AGENCE_1);

        assertEquals(200, doGet("/dashboard" + q("year=2000"), adminA()).status());
        assertEquals(200, doGet("/dashboard" + q("year=3000"), adminA()).status());
        assertEquals(200, doGet("/dashboard" + q("year=2026&month=1"), adminA()).status());
        assertEquals(200, doGet("/dashboard" + q("year=2026&month=12"), adminA()).status());
        assertEquals(5, json(doGet("/dashboard" + q("year=2026&month=2"), adminA())).path("totalFactures").asInt());
        assertEquals(1, json(doGet("/dashboard" + q("year=2026&month=3"), adminA())).path("totalFactures").asInt());
        assertEquals(7, json(doGet("/dashboard" + q("year=2026"), adminA())).path("totalFactures").asInt());
        assertEquals(1, json(doGet("/dashboard" + q("year=2026&month=1"), adminA())).path("totalFactures").asInt());
    }

    @Test
    void le_tableau_de_bord_ne_compte_que_le_centre_demande() throws Exception {
        jeuDeSituations();

        // Nouveau code F4 (KB-FAC-37 corrigé) : 403 quand le centre demandé n'est pas celui de la session.
        golden("centre_b_nouveau_code", doGet("/dashboard?centerId=" + CENTRE_B + "&year=2026&month=2", adminA()));
    }

    @Test
    void le_tableau_de_bord_sans_aucune_facture_est_tout_a_zero() throws Exception {
        golden("aucune_facture", doGet("/dashboard" + q("year=2026"), adminA()));
    }

    // ------------------------------------------------------------------ export

    @Test
    void l_export_csv_liste_les_factures_avec_le_solde_signe_et_les_dates_en_jour_mois_annee() throws Exception {
        jeuDeSituations();

        Resp r = doGet("/export" + q("year=2026&month=2&format=csv"), adminA());

        goldenTexte("csv_fevrier", r.status() + " | " + r.contentType() + " | " + r.disposition(), r.raw());
    }

    @Test
    void l_export_csv_n_a_ni_ligne_de_total_ni_bom_ni_echappement() throws Exception {
        jeuDeSituations();
        jdbc.update("UPDATE factures SET patient_full_name = 'Nom;Avec;Points', numero_facture = 'FAC;0001' WHERE numero_facture = 'FAC-0001'");

        Resp r = doGet("/export" + q("year=2026&month=2&format=csv"), adminA());

        // KB-FAC-44 : les séparateurs « ; » et guillemets d'un libellé ne sont pas échappés.
        assertTrue(r.raw().contains("FAC;0001;"));
        assertEquals(false, r.raw().startsWith("﻿"));
        assertEquals(6, r.raw().split("\n").length);
    }

    @Test
    void l_export_csv_neutralise_les_cellules_de_texte_qui_ressemblent_a_une_formule_nouveau_code() throws Exception {
        jeuDeSituations();
        jdbc.update("UPDATE factures SET numero_facture = '=1+1' WHERE numero_facture = 'FAC-0001'");
        jdbc.update("UPDATE patients SET nom = '-2+3', prenom = '+cmd', numero_assurance = '@cmd' WHERE id = ?", P_AMRANI);

        Resp r = doGet("/export" + q("year=2026&month=2&format=csv"), adminA());

        // KB-FAC-R02 (revue F1-F4, décision du 2026-10-21) : une cellule de texte qui commence par = + - @ reçoit une
        // apostrophe en tête ; les montants numériques (dont le solde négatif d'un trop-perçu) ne sont pas touchés.
        goldenTexte("csv_formules_neutralisees_nouveau_code", r.status() + " | " + r.contentType() + " | " + r.disposition(), r.raw());
        assertTrue(r.raw().contains("'=1+1;'@cmd;'-2+3;'+cmd;"));
    }

    @Test
    void le_format_csv_est_insensible_a_la_casse_et_tout_autre_format_donne_un_classeur_excel() throws Exception {
        jeuDeSituations();

        Resp majuscules = doGet("/export" + q("year=2026&month=2&format=CSV"), adminA());
        Resp xlsx = doGet("/export" + q("year=2026&month=2&format=xlsx"), adminA());
        Resp inconnu = doGet("/export" + q("year=2026&month=2&format=pdf"), adminA());
        Resp defaut = doGet("/export" + q("year=2026&month=2"), adminA());

        assertEquals("text/csv", majuscules.contentType());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx.contentType());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", inconnu.contentType());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", defaut.contentType());
        assertEquals(contenuExcel(xlsx.octets()), contenuExcel(defaut.octets()));
    }

    @Test
    void l_export_excel_reprend_les_memes_colonnes_avec_des_montants_numeriques() throws Exception {
        jeuDeSituations();

        Resp r = doGet("/export" + q("year=2026&month=2"), adminA());

        goldenNode("excel_fevrier_entetes", mapper.createObjectNode().put("entetes", r.status() + " | " + r.contentType() + " | " + r.disposition()));
        goldenNode("excel_fevrier", contenuExcel(r.octets()));
    }

    @Test
    void le_nom_du_fichier_porte_l_annee_seule_meme_pour_un_mois_ou_un_filtre() throws Exception {
        jeuDeSituations();

        Resp mois = doGet("/export" + q("year=2026&month=2&format=csv"), adminA());
        Resp annee = doGet("/export" + q("year=2026&format=csv"), adminA());
        Resp excel = doGet("/export" + q("year=2026&month=2&caisseId=" + CAISSE_1), adminA());

        assertEquals("attachment; filename=\"reglements-2026.csv\"", mois.disposition());
        assertEquals("attachment; filename=\"reglements-2026.csv\"", annee.disposition());
        assertEquals("attachment; filename=\"reglements-2026.xlsx\"", excel.disposition());
    }

    @Test
    void l_export_applique_les_filtres_de_la_recherche_et_n_est_pas_pagine() throws Exception {
        jeuDeSituations();

        Resp annee = doGet("/export" + q("year=2026&format=csv"), adminA());
        Resp caisse = doGet("/export" + q("year=2026&caisseId=" + CAISSE_2 + "&format=csv"), adminA());

        goldenTexte("csv_annee_2026", annee.contentType(), annee.raw());
        goldenTexte("csv_caisse_2", caisse.contentType(), caisse.raw());
    }

    @Test
    void l_export_sans_facture_ne_contient_que_les_entetes() throws Exception {
        Resp csv = doGet("/export" + q("year=2026&format=csv"), adminA());
        Resp xlsx = doGet("/export" + q("year=2026"), adminA());

        goldenTexte("csv_vide", csv.status() + " | " + csv.contentType(), csv.raw());
        goldenNode("excel_vide", contenuExcel(xlsx.octets()));
    }

    @Test
    void l_export_refuse_les_periodes_hors_bornes_et_les_parametres_manquants() throws Exception {
        golden("export_annee_1999", doGet("/export" + q("year=1999&format=csv"), adminA()));
        golden("export_mois_13", doGet("/export" + q("year=2026&month=13"), adminA()));
        golden("export_sans_annee", doGet("/export" + q(""), adminA()));
        golden("export_sans_centre", doGet("/export?year=2026", adminA()));
    }
}
