package com.hemodialyse.backend.characterization.stock.referentiel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Filet de caractérisation S6 — sorties manuelles : {@code POST|GET /api/v1/stock/bons-sortie}, {@code PUT /{id}}, {@code GET
 * /lots-disponibles} (RG-STK-030 à 033). Point d'observation : HTTP et état SQL de {@code bons_sortie(_lignes)}, {@code lots}
 * (quantité restante), {@code stock_movements} (SORTIE, PMP), {@code articles} (stock, PMP) et du compteur {@code SEQ_BS}. Écrit sur
 * le legacy ({@code BonSortieRestController}, {@code BonSortieService.create/update}, {@code SeanceBillingStatusJdbcAdapter}). Les
 * sorties de séance (S6b) ne sont pas ici. Dates relatives au jour d'exécution, normalisées en {@code <J-n>}.
 */
class SortiesManuellesCharacterizationIntegrationTest extends ReferentielStockCharacterizationSupport {

    private static final String BONS = "/api/v1/stock/bons-sortie";
    private static final UUID SEANCE_PAR_DEFAUT = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static String date(int moinsN) {
        return LocalDate.now().minusDays(moinsN).toString();
    }

    private static OffsetDateTime minuit(int moinsN) {
        return LocalDate.now().minusDays(moinsN).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private static String item(UUID article, UUID lot, String quantite) {
        return "{'articleId':" + (article == null ? "null" : "'" + article + "'") + ",'lotId':" + (lot == null ? "null" : "'" + lot + "'")
                + (quantite == null ? "" : ",'quantite':" + quantite) + "}";
    }

    private static String bon(UUID centre, UUID seance, UUID patient, String poste, String dateSortie, String... items) {
        return json("{" + (centre == null ? "" : "'centerId':'" + centre + "',") + (seance == null ? "" : "'seanceId':'" + seance + "',")
                + (patient == null ? "" : "'patientId':'" + patient + "',") + (poste == null ? "" : "'poste':'" + poste + "',")
                + (dateSortie == null ? "" : "'dateSortie':'" + dateSortie + "',") + "'userId':'infirmier-1','items':["
                + String.join(",", items) + "]}");
    }

    @Override
    protected String theme() {
        return "sorties";
    }

    // ------------------------------------------------------------------ données

    @BeforeEach
    @AfterEach
    void viderLesSorties() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM bons_sortie_lignes WHERE bon_sortie_id IN (SELECT id FROM bons_sortie WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM bons_sortie WHERE center_id = ?", c);
            jdbc.update("DELETE FROM inventaires WHERE center_id = ?", c);
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", c);
            jdbc.update("DELETE FROM lots WHERE center_id = ?", c);
            jdbc.update("DELETE FROM seances WHERE center_id = ?", c);
            jdbc.update("DELETE FROM app_settings WHERE center_id = ? AND cle = 'SEQ_BS'", c);
        }
    }

    private UUID art(UUID centre, String alias, String code, boolean actif, String stock, String pmp) {
        return article(centre, alias, code, "Article " + code, null, null, actif, null, stock, pmp);
    }

    /**
     * Un lot approvisionné par une entrée datée, à l'origine de l'historique rejoué.
     */
    private UUID lot(UUID centre, UUID article, String alias, String numero, LocalDate peremption, String quantite, String prix,
                     int joursAvant) {
        UUID lot = UUID.randomUUID();
        jdbc.update("INSERT INTO lots (id, center_id, article_id, numero_lot, date_peremption, quantite_initiale, quantite_restante, pmp, "
                        + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", lot, centre, article, numero, peremption, new BigDecimal(quantite),
                new BigDecimal(quantite), new BigDecimal(prix), minuit(joursAvant));
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, lot_id, mouvement_type, quantite, prix_unitaire, created_by, "
                        + "created_at, saisi_le) VALUES (?, ?, ?, ?, 'ENTREE', ?, ?, 'test', ?, ?)", UUID.randomUUID(), centre, article, lot,
                new BigDecimal(quantite), new BigDecimal(prix), minuit(joursAvant), minuit(joursAvant).plusHours(1));
        aliasNommes.put(lot.toString(), "<" + alias + ">");
        return lot;
    }

    private UUID seance(UUID centre, String alias, String statut) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?, ?, ?, ?, ?, ?)", id,
                UUID.randomUUID(), centre, LocalDate.now().minusDays(2), statut, minuit(2));
        aliasNommes.put(id.toString(), "<" + alias + ">");
        return id;
    }

    // ------------------------------------------------------------------ requêtes

    private Resp jours(Resp r) {
        String brut = r.raw();
        if (brut == null) {
            return r;
        }
        brut = brut.replaceAll("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+(Z|[+-]\\d{2}:?\\d{2})?", "<HORODATAGE>");
        for (int n = -120; n <= 120; n++) {
            String jour = n >= 0 ? "<J-" + n + ">" : "<J+" + (-n) + ">";
            brut = brut.replace(LocalDate.now().minusDays(n).toString(), jour);
            brut = brut.replace(LocalDate.now().minusDays(n).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), jour);
        }
        return new Resp(r.status(), r.contentType(), brut);
    }

    private Resp lire(String chemin, RequestPostProcessor qui) throws Exception {
        return jours(send(get(java.net.URI.create(chemin)).with(qui)));
    }

    private Resp ecrire(boolean ajout, String chemin, String corps, RequestPostProcessor qui) throws Exception {
        var requete = ajout ? post(java.net.URI.create(chemin)) : put(java.net.URI.create(chemin));
        return jours(send(requete.contentType(MediaType.APPLICATION_JSON).content(corps).with(qui)));
    }

    private Resp creer(String corps) throws Exception {
        return ecrire(true, BONS, corps, adminA());
    }

    private Resp modifier(UUID id, String corps) throws Exception {
        return ecrire(false, BONS + "/" + id, corps, adminA());
    }

    private UUID idDe(Resp r, String alias) {
        UUID id = UUID.fromString(json(r).path("id").asString());
        aliasNommes.put(id.toString(), "<" + alias + ">");
        return id;
    }

    private void goldenRequete(String nom, String sql, Object... args) {
        ArrayNode lignes = mapper.createArrayNode();
        for (Map<String, Object> ligne : jdbc.queryForList(sql, args)) {
            ObjectNode o = mapper.createObjectNode();
            ligne.forEach((k, v) -> o.put(k.toLowerCase(), v == null ? null : v instanceof BigDecimal b ? b.toPlainString() : String.valueOf(v)));
            lignes.add(o);
        }
        golden(nom, jours(new Resp(200, "application/json", mapper.writeValueAsString(lignes))));
    }

    private void etat(String prefixe, UUID centre) {
        goldenRequete(prefixe + "_bons", "SELECT reference, poste, CAST(date_sortie AS VARCHAR(10)) AS date_sortie, created_by, "
                + "(seance_id = '" + SEANCE_PAR_DEFAUT + "') AS seance_par_defaut, (patient_id IS NULL) AS sans_patient "
                + "FROM bons_sortie WHERE center_id = ? ORDER BY reference", centre);
        goldenRequete(prefixe + "_lignes", "SELECT b.reference, a.code, l.quantite, l.pmp_applique, o.numero_lot FROM bons_sortie_lignes l "
                + "JOIN bons_sortie b ON b.id = l.bon_sortie_id JOIN articles a ON a.id = l.article_id LEFT JOIN lots o ON o.id = l.lot_id "
                + "WHERE b.center_id = ? ORDER BY b.reference, a.code, o.numero_lot, l.quantite", centre);
        goldenRequete(prefixe + "_lots", "SELECT numero_lot, quantite_initiale, quantite_restante FROM lots WHERE center_id = ? ORDER BY numero_lot", centre);
        goldenRequete(prefixe + "_mouvements", "SELECT a.code, m.mouvement_type, m.quantite, m.prix_unitaire, m.pmp_apres, "
                + "CAST(m.created_at AS VARCHAR(10)) AS jour, m.created_by, (m.seance_id = '" + SEANCE_PAR_DEFAUT + "') AS seance_par_defaut "
                + "FROM stock_movements m JOIN articles a ON a.id = m.article_id WHERE m.center_id = ? "
                + "ORDER BY a.code, m.created_at, m.mouvement_type, m.quantite", centre);
        goldenRequete(prefixe + "_articles", "SELECT code, stock_quantity, pmp_courant FROM articles WHERE center_id = ? ORDER BY code", centre);
    }

    // ------------------------------------------------------------------ création

    @Test
    void la_sortie_d_un_lot_est_valorisee_au_pmp_courant_et_met_a_jour_le_stock() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", true, "150", "9");
        UUID l1 = lot(CENTRE_A, a1, "LOT_1", "L1", LocalDate.now().plusDays(30), "100", "8", 20);
        UUID l2 = lot(CENTRE_A, a1, "LOT_2", "L2", LocalDate.now().plusDays(90), "50", "11", 10);
        UUID patient = UUID.fromString("99997500-0000-0000-0004-000000000001");
        aliasNommes.put(patient.toString(), "<PATIENT_1>");
        golden("creation_nominale_200", creer(bon(CENTRE_A, null, patient, "SALLE_A", date(1), item(a1, l1, "30"), item(a1, l2, "5"))));
        etat("creation_nominale", CENTRE_A);
        goldenRequete("creation_nominale_compteur", "SELECT prefixe, dernier_compteur FROM app_settings WHERE center_id = ? AND cle = 'SEQ_BS'", CENTRE_A);
        golden("creation_sans_date_ni_poste_200", creer(bon(CENTRE_A, null, null, null, null, item(a1, l1, "1"))));
        etat("creation_etat_final", CENTRE_A);
    }

    @Test
    void les_refus_de_creation() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", true, "10", "5");
        UUID a2 = art(CENTRE_A, "ART_2", "A2", true, "10", "5");
        UUID inactif = art(CENTRE_A, "ART_INACTIF", "AI", false, "10", "5");
        UUID b1 = art(CENTRE_B, "ART_B1", "B1", true, "10", "5");
        UUID l1 = lot(CENTRE_A, a1, "LOT_1", "L1", LocalDate.now().plusDays(30), "10", "5", 5);
        UUID l2 = lot(CENTRE_A, a2, "LOT_2", "L2", LocalDate.now().plusDays(30), "10", "5", 5);
        UUID li = lot(CENTRE_A, inactif, "LOT_INACTIF", "LI", LocalDate.now().plusDays(30), "10", "5", 5);
        UUID lb = lot(CENTRE_B, b1, "LOT_B1", "LB1", LocalDate.now().plusDays(30), "10", "5", 5);
        golden("refus_lot_d_un_autre_article", creer(bon(CENTRE_A, null, null, null, date(1), item(a1, l2, "1"))));
        golden("refus_stock_insuffisant_sur_le_lot", creer(bon(CENTRE_A, null, null, null, date(1), item(a1, l1, "10.5"))));
        golden("sortie_du_dernier_stock_du_lot_200", creer(bon(CENTRE_A, null, null, null, date(1), item(a1, l1, "10"))));
        golden("refus_lot_epuise", creer(bon(CENTRE_A, null, null, null, date(1), item(a1, l1, "0.01"))));
        golden("article_inactif_accepte_200", creer(bon(CENTRE_A, null, null, null, date(1), item(inactif, li, "1"))));
        golden("refus_article_inconnu", creer(bon(CENTRE_A, null, null, null, date(1), item(UUID.fromString("99997500-0000-0000-0001-999999999999"), l1, "1"))));
        golden("refus_lot_inconnu", creer(bon(CENTRE_A, null, null, null, date(1), item(a1, UUID.fromString("99997500-0000-0000-0006-000000000001"), "1"))));
        golden("refus_lot_d_un_autre_centre", creer(bon(CENTRE_A, null, null, null, date(1), item(a1, lb, "1"))));
        golden("refus_article_d_un_autre_centre", creer(bon(CENTRE_A, null, null, null, date(1), item(b1, lb, "1"))));
        golden("refus_quantite_nulle", creer(bon(CENTRE_A, null, null, null, date(1), item(a1, l1, "0"))));
        golden("refus_quantite_negative", creer(bon(CENTRE_A, null, null, null, date(1), item(a1, l1, "-1"))));
        golden("refus_ligne_sans_lot", creer(bon(CENTRE_A, null, null, null, date(1), item(a1, null, "1"))));
        golden("refus_ligne_sans_article", creer(bon(CENTRE_A, null, null, null, date(1), item(null, l1, "1"))));
        golden("refus_ligne_sans_quantite", creer(bon(CENTRE_A, null, null, null, date(1), item(a1, l1, null))));
        golden("refus_aucun_article", creer(bon(CENTRE_A, null, null, null, date(1))));
        golden("refus_items_absents", creer(json("{'centerId':'" + CENTRE_A + "'}")));
        golden("refus_centre_absent", creer(json("{'items':[]}")));
        golden("refus_json_illisible", creer("{pas du json"));
        etat("refus_creation", CENTRE_A);
    }

    @Test
    void une_sortie_pour_une_seance_facturee_ou_non_est_creee_sans_controle_de_facturation() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", true, "10", "5");
        UUID l1 = lot(CENTRE_A, a1, "LOT_1", "L1", LocalDate.now().plusDays(30), "10", "5", 5);
        UUID facturee = seance(CENTRE_A, "SEANCE_FACTUREE", "FACTUREE");
        UUID ouverte = seance(CENTRE_A, "SEANCE_OUVERTE", "VALIDEE");
        golden("creation_sur_seance_ouverte_200", creer(bon(CENTRE_A, ouverte, null, "SEANCE", date(2), item(a1, l1, "1"))));
        golden("creation_sur_seance_facturee_200", creer(bon(CENTRE_A, facturee, null, "SEANCE", date(2), item(a1, l1, "1"))));
        etat("creation_seance", CENTRE_A);
    }

    // ------------------------------------------------------------------ correction

    @Test
    void la_correction_restitue_les_lots_puis_sort_les_nouvelles_lignes() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", true, "150", "9");
        UUID l1 = lot(CENTRE_A, a1, "LOT_1", "L1", LocalDate.now().plusDays(30), "100", "8", 20);
        UUID l2 = lot(CENTRE_A, a1, "LOT_2", "L2", LocalDate.now().plusDays(90), "50", "11", 10);
        UUID bon = idDe(creer(bon(CENTRE_A, null, null, "SALLE_A", date(3), item(a1, l1, "30"))), "BON_A");
        golden("correction_200", modifier(bon, bon(CENTRE_A, null, null, null, null, item(a1, l2, "20"), item(a1, l1, "5"))));
        etat("correction", CENTRE_A);
        golden("correction_de_la_date_d_un_bon_manuel_200", modifier(bon, bon(CENTRE_A, null, null, null, date(1), item(a1, l1, "1"))));
        etat("correction_date", CENTRE_A);
        golden("refus_correction_stock_insuffisant", modifier(bon, bon(CENTRE_A, null, null, null, null, item(a1, l1, "500"))));
        golden("refus_correction_aucune_ligne", modifier(bon, bon(CENTRE_A, null, null, null, null)));
        golden("refus_correction_ligne_invalide", modifier(bon, bon(CENTRE_A, null, null, null, null, item(a1, l1, "0"))));
        golden("refus_correction_inconnu", modifier(UUID.fromString("99997500-0000-0000-0007-000000000009"), bon(CENTRE_A, null, null, null, null, item(a1, l1, "1"))));
        etat("correction_apres_refus", CENTRE_A);
    }

    @Test
    void le_bon_d_une_seance_facturee_est_immuable_et_sa_date_ne_change_pas() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", true, "100", "9");
        UUID l1 = lot(CENTRE_A, a1, "LOT_1", "L1", LocalDate.now().plusDays(30), "100", "9", 20);
        UUID ouverte = seance(CENTRE_A, "SEANCE_OUVERTE", "VALIDEE");
        UUID bon = idDe(creer(bon(CENTRE_A, ouverte, null, "SEANCE", date(3), item(a1, l1, "10"))), "BON_SEANCE");
        golden("refus_date_d_un_bon_de_seance_modifiee", modifier(bon, bon(CENTRE_A, null, null, null, date(1), item(a1, l1, "5"))));
        golden("correction_bon_de_seance_meme_date_200", modifier(bon, bon(CENTRE_A, null, null, null, date(3), item(a1, l1, "5"))));
        jdbc.update("UPDATE seances SET statut = 'FACTUREE' WHERE id = ?", ouverte);
        golden("refus_correction_seance_facturee", modifier(bon, bon(CENTRE_A, null, null, null, null, item(a1, l1, "1"))));
        etat("seance_facturee", CENTRE_A);
    }

    // ------------------------------------------------------------------ garde d'inventaire

    @Test
    void un_inventaire_en_cours_ou_une_periode_cloturee_bloquent_les_sorties() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", true, "100", "9");
        UUID l1 = lot(CENTRE_A, a1, "LOT_1", "L1", LocalDate.now().plusDays(30), "100", "9", 20);
        jdbc.update("INSERT INTO inventaires (id, center_id, reference, date_inventaire, statut, created_at) VALUES (?, ?, 'INV-EN-COURS', ?, 'EN_COURS', ?)",
                UUID.randomUUID(), CENTRE_A, LocalDate.now().minusDays(1), minuit(1));
        golden("refus_inventaire_en_cours", creer(bon(CENTRE_A, null, null, null, date(1), item(a1, l1, "1"))));
        jdbc.update("DELETE FROM inventaires WHERE center_id = ?", CENTRE_A);
        jdbc.update("INSERT INTO inventaires (id, center_id, reference, date_inventaire, statut, created_at) VALUES (?, ?, 'INV-CLOTURE', ?, 'CLOTURE', ?)",
                UUID.randomUUID(), CENTRE_A, LocalDate.now().minusDays(5), minuit(5));
        golden("refus_periode_cloturee", creer(bon(CENTRE_A, null, null, null, date(5), item(a1, l1, "1"))));
        golden("sortie_le_lendemain_de_la_cloture_200", creer(bon(CENTRE_A, null, null, null, date(4), item(a1, l1, "1"))));
        etat("garde_inventaire", CENTRE_A);
    }

    // ------------------------------------------------------------------ lots disponibles, lecture, isolation

    @Test
    void les_lots_disponibles_sont_tries_fefo_avec_le_pmp_courant() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", true, "31", "9.5");
        UUID b1 = art(CENTRE_B, "ART_B1", "B1", true, "5", "2");
        lot(CENTRE_A, a1, "LOT_TARD", "L-TARD", LocalDate.now().plusDays(120), "10", "9", 15);
        lot(CENTRE_A, a1, "LOT_TOT", "L-TOT", LocalDate.now().plusDays(10), "5", "9", 15);
        lot(CENTRE_A, a1, "LOT_PERIME", "L-PERIME", LocalDate.now().minusDays(3), "4", "9", 15);
        lot(CENTRE_A, a1, "LOT_SANS_DATE", "L-SANS", null, "6", "9", 15);
        UUID vide = lot(CENTRE_A, a1, "LOT_VIDE", "L-VIDE", LocalDate.now().plusDays(5), "7", "9", 15);
        jdbc.update("UPDATE lots SET quantite_restante = 0 WHERE id = ?", vide);
        lot(CENTRE_B, b1, "LOT_B", "LB", LocalDate.now().plusDays(5), "5", "2", 15);
        golden("lots_disponibles_200", lire(BONS + "/lots-disponibles?centerId=" + CENTRE_A + "&articleId=" + a1, adminA()));
        golden("lots_disponibles_article_inconnu_200", lire(BONS + "/lots-disponibles?centerId=" + CENTRE_A + "&articleId="
                + UUID.fromString("99997500-0000-0000-0001-999999999999"), adminA()));
        golden("lots_disponibles_article_d_un_autre_centre_200", lire(BONS + "/lots-disponibles?centerId=" + CENTRE_A + "&articleId=" + b1, adminA()));
        golden("sans_garde_de_centre_lots_disponibles_autre_centre", lire(BONS + "/lots-disponibles?centerId=" + CENTRE_B + "&articleId=" + b1, adminA()));
        golden("refus_lots_disponibles_sans_centre", lire(BONS + "/lots-disponibles?articleId=" + a1, adminA()));
        golden("refus_lots_disponibles_sans_article", lire(BONS + "/lots-disponibles?centerId=" + CENTRE_A, adminA()));
    }

    @Test
    void la_lecture_la_liste_et_l_absence_d_isolation_de_centre() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", true, "50", "5");
        UUID b1 = art(CENTRE_B, "ART_B1", "B1", true, "50", "5");
        UUID l1 = lot(CENTRE_A, a1, "LOT_1", "L1", LocalDate.now().plusDays(30), "50", "5", 10);
        UUID lb = lot(CENTRE_B, b1, "LOT_B", "LB", LocalDate.now().plusDays(30), "50", "5", 10);
        UUID bonA = idDe(creer(bon(CENTRE_A, null, null, "P1", date(2), item(a1, l1, "1"))), "BON_A");
        jdbc.update("UPDATE bons_sortie SET created_at = ? WHERE id = ?", minuit(2), bonA);
        UUID bonA2 = idDe(creer(bon(CENTRE_A, null, null, "P2", date(1), item(a1, l1, "2"))), "BON_A2");
        jdbc.update("UPDATE bons_sortie SET created_at = ? WHERE id = ?", minuit(1), bonA2);
        UUID bonB = idDe(ecrire(true, BONS, bon(CENTRE_B, null, null, null, date(1), item(b1, lb, "1")), adminB()), "BON_B");
        golden("lecture_200", lire(BONS + "/" + bonA + "?centerId=" + CENTRE_A, adminA()));
        golden("liste_centre_a_200", lire(BONS + "?centerId=" + CENTRE_A, adminA()));
        golden("refus_lecture_inconnu", lire(BONS + "/" + UUID.fromString("99997500-0000-0000-0007-000000000004") + "?centerId=" + CENTRE_A, adminA()));
        golden("refus_lecture_bon_d_un_autre_centre", lire(BONS + "/" + bonB + "?centerId=" + CENTRE_A, adminA()));
        golden("sans_garde_de_centre_lecture_autre_centre", lire(BONS + "/" + bonB + "?centerId=" + CENTRE_B, adminA()));
        golden("sans_garde_de_centre_liste_autre_centre", lire(BONS + "?centerId=" + CENTRE_B, adminA()));
        golden("sans_garde_de_centre_creation_autre_centre", ecrire(true, BONS, bon(CENTRE_B, null, null, null, date(1), item(b1, lb, "1")), adminA()));
        golden("refus_lecture_sans_centre", lire(BONS + "/" + bonA, adminA()));
        golden("refus_liste_sans_centre", lire(BONS, adminA()));
        etat("isolation_centre_b", CENTRE_B);
    }

    // ------------------------------------------------------------------ rôles

    @Test
    void les_roles_d_ecriture_et_de_lecture() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", true, "50", "5");
        UUID l1 = lot(CENTRE_A, a1, "LOT_1", "L1", LocalDate.now().plusDays(30), "50", "5", 10);
        UUID bon = idDe(creer(bon(CENTRE_A, null, null, "P1", date(3), item(a1, l1, "1"))), "BON_A");
        for (String role : List.of("PHARMACIEN", "INFIRMIER")) {
            String r = role.toLowerCase();
            RequestPostProcessor qui = as(CENTRE_A, role);
            golden(r + "_cree_200", ecrire(true, BONS, bon(CENTRE_A, null, null, null, date(1), item(a1, l1, "1")), qui));
            golden(r + "_corrige_200", ecrire(false, BONS + "/" + bon, bon(CENTRE_A, null, null, null, null, item(a1, l1, "2")), qui));
            golden(r + "_lit_200", lire(BONS + "/" + bon + "?centerId=" + CENTRE_A, qui));
            golden(r + "_liste_lots_200", lire(BONS + "/lots-disponibles?centerId=" + CENTRE_A + "&articleId=" + a1, qui));
        }
        for (String role : List.of("MEDECIN", "SECRETAIRE", "DIRECTION")) {
            String r = role.toLowerCase();
            RequestPostProcessor qui = as(CENTRE_A, role);
            golden("refus_creation_" + r, ecrire(true, BONS, bon(CENTRE_A, null, null, null, null), qui));
            golden("refus_correction_" + r, ecrire(false, BONS + "/" + bon, bon(CENTRE_A, null, null, null, null), qui));
            golden("refus_lecture_" + r, lire(BONS + "/" + bon + "?centerId=" + CENTRE_A, qui));
            golden("refus_liste_" + r, lire(BONS + "?centerId=" + CENTRE_A, qui));
        }
        golden("refus_anonyme_creation", jours(send(post(java.net.URI.create(BONS)).contentType(MediaType.APPLICATION_JSON)
                .content(bon(CENTRE_A, null, null, null, null)))));
        etat("roles", CENTRE_A);
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM bons_sortie WHERE center_id = ?", Integer.class, CENTRE_A));
    }
}


