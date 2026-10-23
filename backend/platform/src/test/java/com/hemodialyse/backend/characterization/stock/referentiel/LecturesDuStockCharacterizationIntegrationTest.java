package com.hemodialyse.backend.characterization.stock.referentiel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Filet de caractérisation S4 — lectures du Stock : {@code GET /api/v1/stock/dashboard/{analytics, stock-valorise, alertes,
 * tracabilite/{lotId}, pmp-explain/{articleId}}} (RG-STK-042 à 044, 060, 062). Point d'observation : HTTP uniquement
 * (lectures, aucune écriture) ; les données sont posées en SQL (articles, mouvements, lots, bons) relativement au jour
 * d'exécution, les dates de la réponse sont normalisées en {@code <J-n>} / {@code <J+n>}. Écrit sur le legacy
 * ({@code StockDashboardRestController}, {@code StockDashboardService}, {@code StockDashboardAdapter}).
 */
class LecturesDuStockCharacterizationIntegrationTest extends ReferentielStockCharacterizationSupport {

    private static final String TABLEAU = "/api/v1/stock/dashboard";

    private static OffsetDateTime jour(int moinsN) {
        return LocalDate.now().minusDays(moinsN).atTime(12, 0).atOffset(ZoneOffset.UTC);
    }

    @Override
    protected String theme() {
        return "lectures-publiees";
    }

    // ------------------------------------------------------------------ données

    @BeforeEach
    @AfterEach
    void viderLesLectures() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM bons_sortie_lignes WHERE bon_sortie_id IN (SELECT id FROM bons_sortie WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM bons_sortie WHERE center_id = ?", c);
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", c);
            jdbc.update("DELETE FROM lots WHERE center_id = ?", c);
            jdbc.update("DELETE FROM bons_reception WHERE center_id = ?", c);
        }
    }

    private UUID mouvement(UUID centre, UUID article, String type, String quantite, String prix, OffsetDateTime quand, UUID lot,
                           UUID seance) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, seance_id, lot_id, mouvement_type, quantite, "
                        + "prix_unitaire, created_by, created_at, saisi_le) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'test', ?, ?)",
                id, centre, article, seance, lot, type, new BigDecimal(quantite), prix == null ? null : new BigDecimal(prix), quand,
                quand);
        return id;
    }

    private UUID lot(UUID centre, UUID article, String alias, String numero, LocalDate peremption, String restante, UUID bonReception) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO lots (id, center_id, article_id, bon_reception_id, numero_lot, date_peremption, quantite_initiale, "
                        + "quantite_restante, pmp, created_at) VALUES (?, ?, ?, ?, ?, ?, 100, ?, 10, ?)",
                id, centre, article, bonReception, numero, peremption, new BigDecimal(restante), jour(20));
        aliasNommes.put(id.toString(), "<" + alias + ">");
        return id;
    }

    private UUID bonReception(UUID centre, String alias, String reference) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bons_reception (id, center_id, reference, statut, date_reception, created_by, created_at) "
                + "VALUES (?, ?, ?, 'VALIDE', ?, 'test', ?)", id, centre, reference, LocalDate.now().minusDays(20), jour(20));
        aliasNommes.put(id.toString(), "<" + alias + ">");
        return id;
    }

    private UUID bonSortie(UUID centre, String reference, UUID seance, UUID patient, int jours, UUID article, UUID lot, String qte) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bons_sortie (id, center_id, reference, seance_id, patient_id, date_sortie, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'test', ?)", id, centre, reference, seance, patient, LocalDate.now().minusDays(jours),
                jour(jours));
        jdbc.update("INSERT INTO bons_sortie_lignes (id, bon_sortie_id, article_id, lot_id, quantite, pmp_applique) "
                + "VALUES (?, ?, ?, ?, ?, 10)", UUID.randomUUID(), id, article, lot, new BigDecimal(qte));
        return id;
    }

    private void seuil(UUID article, String seuil) {
        jdbc.update("UPDATE articles SET seuil_alerte = ? WHERE id = ?", new BigDecimal(seuil), article);
    }

    /**
     * Remplace les dates relatives au jour d'exécution par {@code <J-n>} / {@code <J+n>}.
     */
    private Resp jours(Resp r) {
        String brut = r.raw();
        if (brut == null) {
            return r;
        }
        for (int n = -60; n <= 120; n++) {
            brut = brut.replace(LocalDate.now().minusDays(n).toString(), n >= 0 ? "<J-" + n + ">" : "<J+" + (-n) + ">");
        }
        return new Resp(r.status(), r.contentType(), brut);
    }

    private Resp lire(String chemin, RequestPostProcessor qui) throws Exception {
        return jours(send(get(java.net.URI.create(TABLEAU + chemin)).with(qui)));
    }

    private void lecture(String nom, String chemin) throws Exception {
        golden(nom, lire(chemin, adminA()));
    }

    /**
     * Jeu commun : deux articles, des entrées, sorties, ajustements, un inventaire, étalés sur 40 jours.
     */
    private void jeuDeMouvements() {
        UUID a = article(CENTRE_A, "ART_A", "A1", "Alpha", null, null, true, null, "120", "10.5");
        UUID b = article(CENTRE_A, "ART_B", "B1", "Beta", null, null, true, null, "40", "2");
        UUID c = article(CENTRE_A, "ART_C", "C1", "Gamma", null, null, true, null, "5", "100");
        mouvement(CENTRE_A, a, "ENTREE", "100", "8", jour(40), null, null);
        mouvement(CENTRE_A, a, "ENTREE", "50", "12", jour(10), null, null);
        mouvement(CENTRE_A, a, "SORTIE", "30", null, jour(6), null, null);
        mouvement(CENTRE_A, a, "AJUSTEMENT", "10", "9", jour(5), null, null);
        mouvement(CENTRE_A, a, "AJUSTEMENT", "4", null, jour(4), null, null);
        mouvement(CENTRE_A, a, "INVENTAIRE", "120", "10", jour(3), null, null);
        mouvement(CENTRE_A, b, "ENTREE", "200", "2", jour(35), null, null);
        mouvement(CENTRE_A, b, "SORTIE", "160", null, jour(6), null, null);
        mouvement(CENTRE_A, c, "SORTIE", "1", null, jour(0), null, null);
        mouvement(CENTRE_A, c, "ENTREE", "6", "95", jour(100), null, null);
    }

    // ------------------------------------------------------------------ tableau de bord

    @Test
    void le_tableau_de_bord_comble_les_jours_sans_mouvement_et_classe_les_articles() throws Exception {
        jeuDeMouvements();
        lecture("analytics_defaut_200", "/analytics?centerId=" + CENTRE_A);
        lecture("analytics_7_jours_200", "/analytics?centerId=" + CENTRE_A + "&days=7");
        lecture("analytics_90_jours_200", "/analytics?centerId=" + CENTRE_A + "&days=90");
        lecture("analytics_tri_quantite_200", "/analytics?centerId=" + CENTRE_A + "&sortBy=QUANTITY&topN=2");
        lecture("analytics_tri_valeur_top_1_200", "/analytics?centerId=" + CENTRE_A + "&sortBy=VALUE&topN=1");
    }

    @Test
    void les_parametres_du_tableau_de_bord_sont_normalises() throws Exception {
        jeuDeMouvements();
        lecture("analytics_jours_non_autorises_5_200", "/analytics?centerId=" + CENTRE_A + "&days=5");
        lecture("analytics_jours_negatifs_200", "/analytics?centerId=" + CENTRE_A + "&days=-7");
        lecture("analytics_top_nul_200", "/analytics?centerId=" + CENTRE_A + "&topN=0");
        lecture("analytics_top_negatif_200", "/analytics?centerId=" + CENTRE_A + "&topN=-3");
        lecture("analytics_top_au_dela_de_50_200", "/analytics?centerId=" + CENTRE_A + "&topN=500");
        lecture("analytics_tri_inconnu", "/analytics?centerId=" + CENTRE_A + "&sortBy=PRIX");
        lecture("analytics_tri_minuscules", "/analytics?centerId=" + CENTRE_A + "&sortBy=quantity");
        lecture("analytics_tri_vide", "/analytics?centerId=" + CENTRE_A + "&sortBy=");
        lecture("analytics_jours_illisibles", "/analytics?centerId=" + CENTRE_A + "&days=abc");
        lecture("analytics_top_illisible", "/analytics?centerId=" + CENTRE_A + "&topN=abc");
        lecture("analytics_sans_centre", "/analytics");
        lecture("analytics_centre_illisible", "/analytics?centerId=pas-un-uuid");
    }

    @Test
    void le_tableau_de_bord_d_un_centre_sans_mouvement_et_l_isolation_de_centre() throws Exception {
        jeuDeMouvements();
        UUID b1 = article(CENTRE_B, "ART_B1", "BB1", "Beta B", null, null, true, null, "3", "7");
        mouvement(CENTRE_B, b1, "ENTREE", "3", "7", jour(2), null, null);
        golden("analytics_centre_b_200", lire("/analytics?centerId=" + CENTRE_B, adminB()));
        golden("analytics_centre_b_lu_par_l_administrateur_du_centre_a_200", lire("/analytics?centerId=" + CENTRE_B, adminA()));
        golden("analytics_centre_a_lu_par_l_administrateur_du_centre_b_200", lire("/analytics?centerId=" + CENTRE_A, adminB()));
        lecture("analytics_centre_inconnu_200", "/analytics?centerId=" + UUID.fromString("99997500-0000-0000-0000-0000000000ff"));
    }

    @Test
    void la_valeur_d_un_mouvement_sans_prix_est_celle_du_pmp_courant_de_l_article() throws Exception {
        UUID a = article(CENTRE_A, "ART_A", "A1", "Alpha", null, null, true, null, "10", "4");
        mouvement(CENTRE_A, a, "SORTIE", "5", null, jour(1), null, null);
        mouvement(CENTRE_A, a, "SORTIE", "2", "9", jour(1), null, null);
        mouvement(CENTRE_A, a, "INVENTAIRE", "10", "4", jour(1), null, null);
        lecture("analytics_valeur_au_pmp_courant_200", "/analytics?centerId=" + CENTRE_A + "&days=7");
    }

    // ------------------------------------------------------------------ stock valorisé

    @Test
    void le_stock_valorise_liste_les_articles_actifs_par_libelle() throws Exception {
        article(CENTRE_A, "ART_B", "B1", "Beta", null, null, true, null, "40", "2.5");
        article(CENTRE_A, "ART_A", "A1", "alpha", null, null, true, null, "120", "10.5");
        article(CENTRE_A, "ART_INACTIF", "I1", "Inactif", null, null, false, null, "9", "9");
        article(CENTRE_A, "ART_ZERO", "Z1", "Zeta", null, null, true, null, "0", "0");
        article(CENTRE_A, "ART_NEGATIF", "N1", "Negatif", null, null, true, null, "-3", "2");
        article(CENTRE_A, "ART_DECIMAL", "D1", "Decimal", null, null, true, null, "1.234", "3.3333");
        article(CENTRE_B, "ART_B_AUTRE", "BB1", "Autre centre", null, null, true, null, "1", "1");
        lecture("stock_valorise_200", "/stock-valorise?centerId=" + CENTRE_A);
        lecture("stock_valorise_centre_sans_article_200", "/stock-valorise?centerId=" + UUID.fromString("99997500-0000-0000-0000-0000000000ff"));
        golden("stock_valorise_centre_b_lu_par_l_administrateur_du_centre_a_200", lire("/stock-valorise?centerId=" + CENTRE_B, adminA()));
        lecture("stock_valorise_sans_centre", "/stock-valorise");
    }

    // ------------------------------------------------------------------ alertes

    @Test
    void les_alertes_de_peremption_de_rupture_et_de_seuil() throws Exception {
        UUID a = article(CENTRE_A, "ART_A", "A1", "Alpha", null, null, true, null, "50", "2");
        UUID rupture = article(CENTRE_A, "ART_R", "R1", "Rupture", null, null, true, null, "0", "2");
        UUID negatif = article(CENTRE_A, "ART_N", "N1", "Negatif", null, null, true, null, "-1", "2");
        UUID seuil = article(CENTRE_A, "ART_S", "S1", "Seuil", null, null, true, null, "5", "2");
        UUID egalSeuil = article(CENTRE_A, "ART_E", "E1", "Egal seuil", null, null, true, null, "5", "2");
        UUID auDessus = article(CENTRE_A, "ART_D", "D1", "Au dessus", null, null, true, null, "6", "2");
        UUID inactif = article(CENTRE_A, "ART_I", "I1", "Inactif", null, null, false, null, "0", "2");
        UUID autre = article(CENTRE_B, "ART_X", "X1", "Autre centre", null, null, true, null, "0", "2");
        seuil(seuil, "10");
        seuil(egalSeuil, "5");
        seuil(auDessus, "5");
        seuil(inactif, "10");
        lot(CENTRE_A, a, "LOT_PERIME", "L-PERIME", LocalDate.now().minusDays(5), "10", null);
        lot(CENTRE_A, a, "LOT_J30", "L-J30", LocalDate.now().plusDays(30), "10", null);
        lot(CENTRE_A, a, "LOT_J31", "L-J31", LocalDate.now().plusDays(31), "10", null);
        lot(CENTRE_A, a, "LOT_J1", "L-J1", LocalDate.now().plusDays(1), "3", null);
        lot(CENTRE_A, a, "LOT_VIDE", "L-VIDE", LocalDate.now().plusDays(2), "0", null);
        lot(CENTRE_A, a, "LOT_SANS_DATE", "L-SANS-DATE", null, "10", null);
        lot(CENTRE_B, autre, "LOT_AUTRE_CENTRE", "L-AUTRE", LocalDate.now().plusDays(3), "10", null);
        lecture("alertes_200", "/alertes?centerId=" + CENTRE_A);
        golden("alertes_centre_b_200", lire("/alertes?centerId=" + CENTRE_B, adminB()));
        golden("alertes_centre_b_lu_par_l_administrateur_du_centre_a_200", lire("/alertes?centerId=" + CENTRE_B, adminA()));
        lecture("alertes_sans_centre", "/alertes");
    }

    // ------------------------------------------------------------------ traçabilité

    @Test
    void la_tracabilite_d_un_lot_jusqu_au_patient() throws Exception {
        UUID a = article(CENTRE_A, "ART_A", "A1", "Alpha", null, null, true, null, "50", "2");
        UUID b = article(CENTRE_B, "ART_B1", "B1", "Beta B", null, null, true, null, "50", "2");
        UUID lotA = lot(CENTRE_A, a, "LOT_A", "L-A", LocalDate.now().plusDays(90), "10", null);
        UUID lotVide = lot(CENTRE_A, a, "LOT_VIDE", "L-VIDE", LocalDate.now().plusDays(90), "10", null);
        UUID lotB = lot(CENTRE_B, b, "LOT_B", "L-B", LocalDate.now().plusDays(90), "10", null);
        UUID patient = UUID.fromString("99997500-0000-0000-0004-000000000001");
        UUID seance = UUID.fromString("99997500-0000-0000-0005-000000000001");
        aliasNommes.put(patient.toString(), "<PATIENT_1>");
        aliasNommes.put(seance.toString(), "<SEANCE_1>");
        bonSortie(CENTRE_A, "BS-00001", seance, patient, 5, a, lotA, "2");
        bonSortie(CENTRE_A, "BS-00002", UUID.fromString("99997500-0000-0000-0005-000000000002"), null, 1, a, lotA, "3");
        bonSortie(CENTRE_A, "BS-00003", UUID.fromString("99997500-0000-0000-0005-000000000003"), patient, 9, a, lotA, "1.5");
        bonSortie(CENTRE_B, "BS-00004", UUID.fromString("99997500-0000-0000-0005-000000000004"), patient, 2, b, lotB, "4");
        golden("tracabilite_lot_200", lire("/tracabilite/" + lotA + "?centerId=" + CENTRE_A, adminA()));
        golden("tracabilite_lot_sans_sortie_200", lire("/tracabilite/" + lotVide + "?centerId=" + CENTRE_A, adminA()));
        golden("tracabilite_lot_inconnu_200", lire("/tracabilite/" + UUID.fromString("99997500-0000-0000-0006-000000000001")
                + "?centerId=" + CENTRE_A, adminA()));
        golden("tracabilite_lot_d_un_autre_centre_200", lire("/tracabilite/" + lotB + "?centerId=" + CENTRE_A, adminA()));
        golden("tracabilite_lot_b_lu_par_l_administrateur_du_centre_a_200", lire("/tracabilite/" + lotB + "?centerId=" + CENTRE_B, adminA()));
        golden("tracabilite_identifiant_illisible", lire("/tracabilite/pas-un-uuid?centerId=" + CENTRE_A, adminA()));
        golden("tracabilite_sans_centre", lire("/tracabilite/" + lotA, adminA()));
    }

    // ------------------------------------------------------------------ explication du PMP

    @Test
    void l_explication_du_pmp_mouvement_par_mouvement() throws Exception {
        UUID a = article(CENTRE_A, "ART_A", "A1", "Alpha", null, null, true, null, "100", "10");
        UUID br = bonReception(CENTRE_A, "BR_1", "BR-00001");
        UUID br2 = bonReception(CENTRE_A, "BR_2", "BR-00002");
        UUID lot1 = lot(CENTRE_A, a, "LOT_1", "L-1", LocalDate.now().plusDays(90), "10", br);
        UUID lot2 = lot(CENTRE_A, a, "LOT_2", "L-2", LocalDate.now().plusDays(90), "10", br2);
        UUID lotOrphelin = lot(CENTRE_A, a, "LOT_ORPHELIN", "L-3", LocalDate.now().plusDays(90), "10", null);
        UUID seance = UUID.fromString("99997500-0000-0000-0005-000000000011");
        bonSortie(CENTRE_A, "BS-00011", seance, null, 9, a, lot1, "5");
        mouvement(CENTRE_A, a, "ENTREE", "100", "8", jour(30), lot1, null);
        mouvement(CENTRE_A, a, "ENTREE", "50", "11", jour(20), lot2, null);
        mouvement(CENTRE_A, a, "ENTREE", "10", "20", jour(19), null, null);
        mouvement(CENTRE_A, a, "ENTREE", "10", "20", jour(18), lotOrphelin, null);
        mouvement(CENTRE_A, a, "SORTIE", "5", null, jour(9), lot1, seance);
        mouvement(CENTRE_A, a, "SORTIE", "7", null, jour(8), null, null);
        mouvement(CENTRE_A, a, "SORTIE", "2", null, jour(8).plusMinutes(5), lot2, UUID.fromString("99997500-0000-0000-0005-000000000012"));
        mouvement(CENTRE_A, a, "AJUSTEMENT", "10", "15", jour(7), null, null);
        mouvement(CENTRE_A, a, "AJUSTEMENT", "3", null, jour(6), null, null);
        mouvement(CENTRE_A, a, "INVENTAIRE", "120", "10", jour(5), null, null);
        mouvement(CENTRE_A, a, "SORTIE", "500", null, jour(4), null, null);
        mouvement(CENTRE_A, a, "ENTREE", "10", "4", jour(3), null, null);
        golden("pmp_explain_200", lire("/pmp-explain/" + a + "?centerId=" + CENTRE_A, adminA()));
    }

    @Test
    void l_explication_du_pmp_d_un_article_sans_mouvement_inconnu_ou_d_un_autre_centre() throws Exception {
        UUID a = article(CENTRE_A, "ART_A", "A1", "Alpha", null, null, true, null, "0", "0");
        UUID inactif = article(CENTRE_A, "ART_I", "I1", "Inactif", null, null, false, null, "0", "0");
        UUID b = article(CENTRE_B, "ART_B1", "B1", "Beta B", null, null, true, null, "0", "0");
        mouvement(CENTRE_B, b, "ENTREE", "10", "3", jour(2), null, null);
        golden("pmp_explain_sans_mouvement_200", lire("/pmp-explain/" + a + "?centerId=" + CENTRE_A, adminA()));
        golden("pmp_explain_article_inactif_200", lire("/pmp-explain/" + inactif + "?centerId=" + CENTRE_A, adminA()));
        golden("pmp_explain_article_inconnu", lire("/pmp-explain/" + UUID.fromString("99997500-0000-0000-0001-999999999999")
                + "?centerId=" + CENTRE_A, adminA()));
        golden("pmp_explain_article_d_un_autre_centre", lire("/pmp-explain/" + b + "?centerId=" + CENTRE_A, adminA()));
        golden("pmp_explain_centre_b_lu_par_l_administrateur_du_centre_a_200", lire("/pmp-explain/" + b + "?centerId=" + CENTRE_B, adminA()));
        golden("pmp_explain_identifiant_illisible", lire("/pmp-explain/pas-un-uuid?centerId=" + CENTRE_A, adminA()));
        golden("pmp_explain_sans_centre", lire("/pmp-explain/" + a, adminA()));
    }

    // ------------------------------------------------------------------ rôles

    @Test
    void les_roles_de_lecture() throws Exception {
        UUID a = article(CENTRE_A, "ART_A", "A1", "Alpha", null, null, true, null, "10", "2");
        UUID lotA = lot(CENTRE_A, a, "LOT_A", "L-A", LocalDate.now().plusDays(10), "10", null);
        mouvement(CENTRE_A, a, "ENTREE", "10", "2", jour(1), lotA, null);
        for (String role : List.of("PHARMACIEN", "INFIRMIER", "MEDECIN", "SECRETAIRE", "DIRECTION")) {
            String r = role.toLowerCase();
            RequestPostProcessor qui = as(CENTRE_A, role);
            golden("role_" + r + "_analytics", lire("/analytics?centerId=" + CENTRE_A, qui));
            golden("role_" + r + "_stock_valorise", lire("/stock-valorise?centerId=" + CENTRE_A, qui));
            golden("role_" + r + "_alertes", lire("/alertes?centerId=" + CENTRE_A, qui));
            golden("role_" + r + "_pmp_explain", lire("/pmp-explain/" + a + "?centerId=" + CENTRE_A, qui));
            golden("role_" + r + "_tracabilite", lire("/tracabilite/" + lotA + "?centerId=" + CENTRE_A, qui));
        }
        golden("role_anonyme_analytics", jours(send(get(java.net.URI.create(TABLEAU + "/analytics?centerId=" + CENTRE_A)))));
        golden("role_anonyme_tracabilite", jours(send(get(java.net.URI.create(TABLEAU + "/tracabilite/" + lotA + "?centerId=" + CENTRE_A)))));
    }
}


