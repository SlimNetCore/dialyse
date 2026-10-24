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
 * Filet de caractérisation S5 — réceptions et PMP : {@code POST|GET /api/v1/stock/bons-reception}, {@code POST
 * /from-bon-commande}, {@code PUT /{id}}, {@code POST /{id}/valider} et le recalcul massif {@code POST
 * /api/v1/stock/dashboard/pmp-recalc/start}, {@code GET /pmp-recalc/jobs/{id}}, {@code GET /pmp-recalc/locks} (RG-STK-020 à
 * 025, 040, 041). Point d'observation : HTTP et état SQL de {@code bons_reception(_lignes)}, {@code lots}, {@code
 * stock_movements}, {@code articles} (stock, PMP), {@code bons_commande} (statut {@code RECU}) et du compteur {@code SEQ_BR}.
 * Écrit sur le legacy ({@code BonReceptionRestController}, {@code BonReceptionService}, {@code PmpEngine}, {@code
 * PmpRecalculationCoordinator}, {@code InventoryGuardedMovementRepository}). Dates relatives au jour d'exécution,
 * normalisées en {@code <J-n>}.
 */
class ReceptionsEtPmpCharacterizationIntegrationTest extends ReferentielStockCharacterizationSupport {

    private static final String BONS = "/api/v1/stock/bons-reception";
    private static final String DASHBOARD = "/api/v1/stock/dashboard";

    private static String date(int moinsN) {
        return LocalDate.now().minusDays(moinsN).toString();
    }

    private static OffsetDateTime minuit(int moinsN) {
        return LocalDate.now().minusDays(moinsN).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private static String ligne(UUID article, String quantite, String prix, String numeroLot, String peremption) {
        return "{'articleId':" + (article == null ? "null" : "'" + article + "'")
                + (quantite == null ? "" : ",'quantite':" + quantite)
                + (prix == null ? "" : ",'prixUnitaire':" + prix)
                + (numeroLot == null ? "" : ",'numeroLot':'" + numeroLot + "'")
                + (peremption == null ? "" : ",'datePeremption':'" + peremption + "'") + "}";
    }

    private static String bon(UUID centre, UUID fournisseur, UUID bonCommande, String dateReception, String... lignes) {
        return json("{" + (centre == null ? "" : "'centerId':'" + centre + "',")
                + (fournisseur == null ? "" : "'fournisseurId':'" + fournisseur + "',")
                + (bonCommande == null ? "" : "'bonCommandeId':'" + bonCommande + "',")
                + (dateReception == null ? "" : "'dateReception':'" + dateReception + "',")
                + "'userId':'pharma-1','lignes':[" + String.join(",", lignes) + "]}");
    }

    private static String action(UUID centre) {
        return json("{'centerId':'" + centre + "','userId':'pharma-1'}");
    }

    @Override
    protected String theme() {
        return "receptions";
    }

    // ------------------------------------------------------------------ données

    @BeforeEach
    @AfterEach
    void viderLesReceptions() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM bons_reception_lignes WHERE bon_reception_id IN (SELECT id FROM bons_reception WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM bons_reception WHERE center_id = ?", c);
            jdbc.update("DELETE FROM bons_commande_lignes WHERE bon_commande_id IN (SELECT id FROM bons_commande WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM bons_commande WHERE center_id = ?", c);
            jdbc.update("DELETE FROM inventaires WHERE center_id = ?", c);
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", c);
            jdbc.update("DELETE FROM lots WHERE center_id = ?", c);
            jdbc.update("DELETE FROM app_settings WHERE center_id = ? AND cle IN ('SEQ_BR', 'SEQ_BC')", c);
        }
    }

    private UUID art(UUID centre, String alias, String code, boolean gereParLot, String stock, String pmp) {
        UUID id = article(centre, alias, code, "Article " + code, null, null, true, null, stock, pmp);
        jdbc.update("UPDATE articles SET gere_par_lot = ? WHERE id = ?", gereParLot, id);
        return id;
    }

    private UUID bonCommandeValide(UUID centre, String alias, UUID fournisseur, UUID article, String quantite, String prix) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bons_commande (id, center_id, reference, fournisseur_id, statut, created_by, created_at) "
                + "VALUES (?, ?, 'BC-00001', ?, 'VALIDE', 'test', ?)", id, centre, fournisseur, minuit(3));
        jdbc.update("INSERT INTO bons_commande_lignes (id, bon_commande_id, article_id, quantite, prix_unitaire) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), id, article, new BigDecimal(quantite), new BigDecimal(prix));
        aliasNommes.put(id.toString(), "<" + alias + ">");
        return id;
    }

    private void inventaire(UUID centre, String statut, int joursAvant) {
        jdbc.update("INSERT INTO inventaires (id, center_id, reference, date_inventaire, statut, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), centre, "INV-" + statut, LocalDate.now().minusDays(joursAvant), statut, minuit(joursAvant));
    }

    private UUID sortie(UUID centre, UUID article, String quantite, OffsetDateTime quand) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, mouvement_type, quantite, prix_unitaire, pmp_apres, "
                        + "created_by, created_at, saisi_le) VALUES (?, ?, ?, 'SORTIE', ?, 99, 99, 'test', ?, ?)",
                id, centre, article, new BigDecimal(quantite), quand, quand.plusHours(1));
        return id;
    }

    // ------------------------------------------------------------------ requêtes

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

    private Resp valider(UUID id, RequestPostProcessor qui, UUID centre) throws Exception {
        return ecrire(true, BONS + "/" + id + "/valider", action(centre), qui);
    }

    private UUID idDe(Resp r, String alias) {
        UUID id = UUID.fromString(json(r).path("id").asString());
        aliasNommes.put(id.toString(), "<" + alias + ">");
        return id;
    }

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

    /**
     * Rend le résultat d'une requête SQL en JSON, dates relatives normalisées.
     */
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
        goldenRequete(prefixe + "_bons", "SELECT reference, statut, created_by, CAST(date_reception AS VARCHAR(10)) AS date_reception, "
                + "(bon_commande_id IS NULL) AS sans_bon_commande, (fournisseur_id IS NULL) AS sans_fournisseur "
                + "FROM bons_reception WHERE center_id = ? ORDER BY reference", centre);
        goldenRequete(prefixe + "_lignes", "SELECT b.reference, l.quantite, l.prix_unitaire, l.numero_lot, "
                + "CAST(l.date_peremption AS VARCHAR(10)) AS date_peremption, (l.lot_id IS NULL) AS sans_lot "
                + "FROM bons_reception_lignes l JOIN bons_reception b ON b.id = l.bon_reception_id WHERE b.center_id = ? "
                + "ORDER BY b.reference, l.quantite, l.prix_unitaire", centre);
        goldenRequete(prefixe + "_lots", "SELECT numero_lot, CAST(date_peremption AS VARCHAR(10)) AS date_peremption, quantite_initiale, "
                + "quantite_restante, pmp, (bon_reception_id IS NULL) AS sans_bon FROM lots WHERE center_id = ? ORDER BY numero_lot, quantite_initiale", centre);
        goldenRequete(prefixe + "_mouvements", "SELECT a.code, m.mouvement_type, m.quantite, m.prix_unitaire, m.pmp_apres, "
                + "CAST(m.created_at AS VARCHAR(10)) AS jour, m.created_by, (m.lot_id IS NULL) AS sans_lot FROM stock_movements m "
                + "JOIN articles a ON a.id = m.article_id WHERE m.center_id = ? ORDER BY a.code, m.created_at, m.mouvement_type DESC, m.quantite", centre);
        goldenRequete(prefixe + "_articles", "SELECT code, stock_quantity, pmp_courant FROM articles WHERE center_id = ? ORDER BY code", centre);
    }

    // ------------------------------------------------------------------ création

    @Test
    void la_creation_d_un_brouillon_ne_touche_ni_les_lots_ni_les_mouvements() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", false, "0", "0");
        UUID a2 = art(CENTRE_A, "ART_2", "A2", true, "0", "0");
        UUID f = fournisseur(CENTRE_A, "FOUR_1", "Fournisseur 1", true);
        golden("creation_nominale_200", creer(bon(CENTRE_A, f, null, date(4),
                ligne(a1, "10", "2.5", null, null), ligne(a2, "3", "1.333", "LOT-X", date(-90)))));
        etat("creation_nominale", CENTRE_A);
        goldenRequete("creation_nominale_compteur", "SELECT prefixe, dernier_compteur FROM app_settings WHERE center_id = ? AND cle = 'SEQ_BR'", CENTRE_A);
        golden("creation_sans_date_200", creer(bon(CENTRE_A, null, null, null, ligne(a1, "1", "1", null, null))));
        golden("creation_sans_ligne_200", creer(bon(CENTRE_A, null, null, date(1))));
        golden("creation_ligne_invalide_acceptee_en_brouillon_200", creer(bon(CENTRE_A, null, null, date(1),
                ligne(a1, "-5", "-1", null, null))));
        etat("creation_etat_final", CENTRE_A);
    }

    @Test
    void la_creation_depuis_un_bon_de_commande() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", false, "0", "0");
        UUID b1 = art(CENTRE_B, "ART_B1", "B1", false, "0", "0");
        UUID f = fournisseur(CENTRE_A, "FOUR_1", "Fournisseur 1", true);
        UUID bc = bonCommandeValide(CENTRE_A, "BC_A", f, a1, "7", "3.5");
        UUID bcB = bonCommandeValide(CENTRE_B, "BC_B", null, b1, "2", "1");
        golden("depuis_bon_de_commande_200", ecrire(true, BONS + "/from-bon-commande",
                json("{'centerId':'" + CENTRE_A + "','bonCommandeId':'" + bc + "','userId':'pharma-1'}"), adminA()));
        etat("depuis_bon_de_commande", CENTRE_A);
        golden("refus_bon_de_commande_inconnu", ecrire(true, BONS + "/from-bon-commande",
                json("{'centerId':'" + CENTRE_A + "','bonCommandeId':'" + UUID.fromString("99997500-0000-0000-0007-000000000001") + "'}"), adminA()));
        golden("refus_bon_de_commande_d_un_autre_centre", ecrire(true, BONS + "/from-bon-commande",
                json("{'centerId':'" + CENTRE_A + "','bonCommandeId':'" + bcB + "'}"), adminA()));
        golden("refus_sans_bon_de_commande", ecrire(true, BONS + "/from-bon-commande", json("{'centerId':'" + CENTRE_A + "'}"), adminA()));
    }

    // ------------------------------------------------------------------ modification d'un brouillon

    @Test
    void la_modification_d_un_brouillon_remplace_les_lignes_et_conserve_ce_qui_est_omis() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", false, "0", "0");
        UUID f = fournisseur(CENTRE_A, "FOUR_1", "Fournisseur 1", true);
        UUID bon = idDe(creer(bon(CENTRE_A, f, null, date(5), ligne(a1, "1", "1", null, null))), "BON_A");
        golden("modification_200", modifier(bon, bon(CENTRE_A, null, null, null, ligne(a1, "9", "4", null, null), ligne(a1, "2", "5", "L1", null))));
        etat("modification", CENTRE_A);
        golden("modification_par_aucune_ligne_200", modifier(bon, bon(CENTRE_A, null, null, date(2))));
        etat("modification_vide", CENTRE_A);
        golden("refus_modification_inconnu", modifier(UUID.fromString("99997500-0000-0000-0007-000000000002"), bon(CENTRE_A, null, null, null)));
        golden("refus_modification_centre_absent", modifier(bon, json("{'lignes':[]}")));
    }

    // ------------------------------------------------------------------ validation

    @Test
    void la_validation_cree_les_lots_les_mouvements_le_pmp_et_marque_le_bon_de_commande_recu() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", false, "0", "0");
        UUID a2 = art(CENTRE_A, "ART_2", "A2", true, "0", "0");
        UUID f = fournisseur(CENTRE_A, "FOUR_1", "Fournisseur 1", true);
        UUID bc = bonCommandeValide(CENTRE_A, "BC_A", f, a1, "10", "2");
        UUID bon = idDe(creer(bon(CENTRE_A, f, bc, date(6), ligne(a1, "10", "2.5", null, null), ligne(a1, "5", "3.2", null, null),
                ligne(a2, "20", "1.25", "LOT-X", date(-60)))), "BON_A");
        golden("validation_200", valider(bon, adminA(), CENTRE_A));
        etat("validation", CENTRE_A);
        goldenRequete("validation_bon_de_commande", "SELECT reference, statut FROM bons_commande WHERE center_id = ?", CENTRE_A);
        golden("refus_seconde_validation", valider(bon, adminA(), CENTRE_A));
        etat("validation_apres_refus", CENTRE_A);
        golden("lecture_apres_validation_200", lire(BONS + "/" + bon + "?centerId=" + CENTRE_A, adminA()));
    }

    @Test
    void le_pmp_se_recalcule_sur_tout_l_historique_et_les_entrees_precedent_les_sorties_du_meme_jour() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", false, "0", "0");
        UUID premier = idDe(creer(bon(CENTRE_A, null, null, date(10), ligne(a1, "100", "8", null, null))), "BON_1");
        assertEquals(200, valider(premier, adminA(), CENTRE_A).status());
        sortie(CENTRE_A, a1, "30", minuit(5));
        UUID second = idDe(creer(bon(CENTRE_A, null, null, date(5), ligne(a1, "50", "11", null, null))), "BON_2");
        golden("validation_entree_du_jour_d_une_sortie_200", valider(second, adminA(), CENTRE_A));
        etat("rejeu_meme_jour", CENTRE_A);
        UUID troisieme = idDe(creer(bon(CENTRE_A, null, null, date(20), ligne(a1, "10", "20", null, null))), "BON_3");
        golden("validation_entree_anterieure_200", valider(troisieme, adminA(), CENTRE_A));
        etat("rejeu_entree_anterieure", CENTRE_A);
    }

    @Test
    void les_refus_de_validation_n_ecrivent_rien() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", false, "0", "0");
        UUID gere = art(CENTRE_A, "ART_G", "G1", true, "0", "0");
        UUID vide = idDe(creer(bon(CENTRE_A, null, null, date(3))), "BON_VIDE");
        golden("refus_validation_sans_ligne", valider(vide, adminA(), CENTRE_A));
        UUID sansNumero = idDe(creer(bon(CENTRE_A, null, null, date(3), ligne(a1, "1", "1", null, null),
                ligne(gere, "1", "1", null, date(30)))), "BON_SANS_NUMERO");
        golden("refus_lot_gere_sans_numero", valider(sansNumero, adminA(), CENTRE_A));
        UUID sansPeremption = idDe(creer(bon(CENTRE_A, null, null, date(3), ligne(gere, "1", "1", "L1", null))), "BON_SANS_PEREMPTION");
        golden("refus_lot_gere_sans_peremption", valider(sansPeremption, adminA(), CENTRE_A));
        golden("refus_quantite_nulle", creer(bon(CENTRE_A, null, null, date(3), ligne(a1, "0", "1", null, null))));
        golden("creation_ligne_sans_article", creer(bon(CENTRE_A, null, null, date(3), ligne(null, "1", "1", null, null))));
        golden("refus_article_inconnu", creer(bon(CENTRE_A, null, null, date(3),
                ligne(UUID.fromString("99997500-0000-0000-0001-999999999999"), "1", "1", null, null))));
        golden("refus_validation_inconnu", valider(UUID.fromString("99997500-0000-0000-0007-000000000003"), adminA(), CENTRE_A));
        etat("refus_validation", CENTRE_A);
    }

    // ------------------------------------------------------------------ correction d'un bon validé

    @Test
    void la_correction_d_un_bon_valide_rejoue_le_pmp() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", false, "0", "0");
        UUID bon = idDe(creer(bon(CENTRE_A, null, null, date(8), ligne(a1, "100", "8", null, null))), "BON_A");
        assertEquals(200, valider(bon, adminA(), CENTRE_A).status());
        sortie(CENTRE_A, a1, "30", minuit(4));
        golden("correction_quantite_et_prix_200", modifier(bon, bon(CENTRE_A, null, null, null, ligne(a1, "120", "9.5", null, null))));
        etat("correction", CENTRE_A);
        golden("correction_de_la_date_200", modifier(bon, bon(CENTRE_A, null, null, date(9))));
        etat("correction_date", CENTRE_A);
        golden("correction_a_10_alors_que_30_sortis_sans_lot_200", modifier(bon, bon(CENTRE_A, null, null, null, ligne(a1, "10", "9.5", null, null))));
        assertEquals(200, modifier(bon, bon(CENTRE_A, null, null, null, ligne(a1, "120", "9.5", null, null))).status());
        jdbc.update("UPDATE lots SET quantite_restante = 50 WHERE center_id = ?", CENTRE_A);
        golden("refus_correction_inferieure_a_la_quantite_consommee_du_lot", modifier(bon, bon(CENTRE_A, null, null, null, ligne(a1, "60", "9.5", null, null))));
        golden("correction_egale_a_la_quantite_consommee_du_lot_200", modifier(bon, bon(CENTRE_A, null, null, null, ligne(a1, "70", "9.5", null, null))));
        golden("refus_correction_nombre_de_lignes", modifier(bon, bon(CENTRE_A, null, null, null, ligne(a1, "1", "1", null, null),
                ligne(a1, "1", "1", null, null))));
        UUID autre = art(CENTRE_A, "ART_2", "A2", false, "0", "0");
        golden("refus_correction_article_modifie", modifier(bon, bon(CENTRE_A, null, null, null, ligne(autre, "1", "1", null, null))));
        golden("refus_correction_sans_prix", modifier(bon, bon(CENTRE_A, null, null, null, ligne(a1, "1", null, null, null))));
        etat("correction_apres_refus", CENTRE_A);
    }

    // ------------------------------------------------------------------ garde d'inventaire

    @Test
    void un_inventaire_en_cours_ou_une_periode_cloturee_bloquent_la_validation() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", false, "0", "0");
        UUID bon = idDe(creer(bon(CENTRE_A, null, null, date(5), ligne(a1, "10", "2", null, null))), "BON_A");
        inventaire(CENTRE_A, "EN_COURS", 1);
        golden("refus_inventaire_en_cours", valider(bon, adminA(), CENTRE_A));
        jdbc.update("DELETE FROM inventaires WHERE center_id = ?", CENTRE_A);
        inventaire(CENTRE_A, "CLOTURE", 5);
        golden("refus_periode_cloturee_meme_jour", valider(bon, adminA(), CENTRE_A));
        UUID apres = idDe(creer(bon(CENTRE_A, null, null, date(4), ligne(a1, "10", "2", null, null))), "BON_APRES");
        golden("validation_le_lendemain_de_la_cloture_200", valider(apres, adminA(), CENTRE_A));
        etat("garde_inventaire", CENTRE_A);
    }

    // ------------------------------------------------------------------ recalcul massif

    @Test
    void le_recalcul_massif_du_pmp() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", false, "0", "0");
        UUID a2 = art(CENTRE_A, "ART_2", "A2", false, "0", "0");
        UUID bon = idDe(creer(bon(CENTRE_A, null, null, date(6), ligne(a1, "100", "8", null, null), ligne(a2, "10", "5", null, null))), "BON_A");
        assertEquals(200, valider(bon, adminA(), CENTRE_A).status());
        jdbc.update("UPDATE articles SET pmp_courant = 1, stock_quantity = 1 WHERE center_id = ?", CENTRE_A);
        Resp demarrage = ecrire(true, DASHBOARD + "/pmp-recalc/start", json("{'centerId':'" + CENTRE_A + "','articleIds':['" + a1 + "','" + a2 + "','" + a1 + "']}"), adminA());
        UUID job = UUID.fromString(json(demarrage).path("jobId").asString());
        aliasNommes.put(job.toString(), "<JOB>");
        golden("recalcul_demarrage_200", demarrage);
        Resp suivi = null;
        for (int i = 0; i < 100; i++) {
            suivi = lire(DASHBOARD + "/pmp-recalc/jobs/" + job, adminA());
            String statut = json(suivi).path("status").asString();
            if ("COMPLETED".equals(statut) || "FAILED".equals(statut)) {
                break;
            }
            Thread.sleep(100);
        }
        golden("recalcul_termine_200", suivi);
        etat("recalcul", CENTRE_A);
        golden("recalcul_verrous_apres_200", lire(DASHBOARD + "/pmp-recalc/locks?centerId=" + CENTRE_A, adminA()));
        golden("recalcul_sans_article", ecrire(true, DASHBOARD + "/pmp-recalc/start", json("{'centerId':'" + CENTRE_A + "','articleIds':[]}"), adminA()));
        golden("recalcul_job_inconnu", lire(DASHBOARD + "/pmp-recalc/jobs/" + UUID.fromString("99997500-0000-0000-0008-000000000001"), adminA()));
        golden("recalcul_article_inconnu_termine_200", jours(ecrire(true, DASHBOARD + "/pmp-recalc/start",
                json("{'centerId':'" + CENTRE_A + "','articleIds':['" + UUID.fromString("99997500-0000-0000-0001-999999999999") + "']}"), adminA())));
    }

    // ------------------------------------------------------------------ lecture, liste, isolation

    @Test
    void la_lecture_la_liste_et_l_absence_d_isolation_de_centre() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", false, "0", "0");
        UUID b1 = art(CENTRE_B, "ART_B1", "B1", false, "0", "0");
        UUID bonA = idDe(creer(bon(CENTRE_A, null, null, date(5), ligne(a1, "1", "1", null, null))), "BON_A");
        jdbc.update("UPDATE bons_reception SET created_at = ? WHERE id = ?", minuit(3), bonA);
        UUID bonA2 = idDe(creer(bon(CENTRE_A, null, null, date(2), ligne(a1, "2", "2", null, null))), "BON_A2");
        jdbc.update("UPDATE bons_reception SET created_at = ? WHERE id = ?", minuit(1), bonA2);
        UUID bonB = idDe(ecrire(true, BONS, bon(CENTRE_B, null, null, date(2), ligne(b1, "1", "1", null, null)), adminB()), "BON_B");
        golden("lecture_200", lire(BONS + "/" + bonA + "?centerId=" + CENTRE_A, adminA()));
        golden("liste_centre_a_200", lire(BONS + "?centerId=" + CENTRE_A, adminA()));
        golden("refus_lecture_inconnu", lire(BONS + "/" + UUID.fromString("99997500-0000-0000-0007-000000000004") + "?centerId=" + CENTRE_A, adminA()));
        golden("refus_lecture_bon_d_un_autre_centre", lire(BONS + "/" + bonB + "?centerId=" + CENTRE_A, adminA()));
        golden("sans_garde_de_centre_lecture_autre_centre", lire(BONS + "/" + bonB + "?centerId=" + CENTRE_B, adminA()));
        golden("sans_garde_de_centre_liste_autre_centre", lire(BONS + "?centerId=" + CENTRE_B, adminA()));
        golden("sans_garde_de_centre_creation_autre_centre", ecrire(true, BONS, bon(CENTRE_B, null, null, date(1), ligne(b1, "1", "1", null, null)), adminA()));
        golden("sans_garde_de_centre_validation_autre_centre", valider(bonB, adminA(), CENTRE_B));
        etat("sans_garde_centre_b", CENTRE_B);
        golden("refus_lecture_sans_centre", lire(BONS + "/" + bonA, adminA()));
        golden("refus_liste_sans_centre", lire(BONS, adminA()));
        golden("refus_creation_json_illisible", ecrire(true, BONS, "{pas du json", adminA()));
        golden("refus_creation_centre_absent", ecrire(true, BONS, json("{'lignes':[]}"), adminA()));
    }

    // ------------------------------------------------------------------ rôles

    @Test
    void les_roles_d_ecriture_et_de_lecture() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", "A1", false, "0", "0");
        UUID bon = idDe(creer(bon(CENTRE_A, null, null, date(3), ligne(a1, "1", "1", null, null))), "BON_A");
        golden("pharmacien_cree_200", ecrire(true, BONS, bon(CENTRE_A, null, null, date(1), ligne(a1, "1", "1", null, null)), as(CENTRE_A, "PHARMACIEN")));
        golden("pharmacien_modifie_200", ecrire(false, BONS + "/" + bon, bon(CENTRE_A, null, null, null, ligne(a1, "3", "3", null, null)), as(CENTRE_A, "PHARMACIEN")));
        golden("pharmacien_valide_200", valider(bon, as(CENTRE_A, "PHARMACIEN"), CENTRE_A));
        for (String role : List.of("INFIRMIER", "MEDECIN", "SECRETAIRE", "DIRECTION")) {
            String r = role.toLowerCase();
            RequestPostProcessor qui = as(CENTRE_A, role);
            golden("refus_creation_" + r, ecrire(true, BONS, bon(CENTRE_A, null, null, null), qui));
            golden("refus_validation_" + r, valider(bon, qui, CENTRE_A));
            golden("refus_recalcul_" + r, ecrire(true, DASHBOARD + "/pmp-recalc/start", json("{'centerId':'" + CENTRE_A + "','articleIds':['" + a1 + "']}"), qui));
        }
        golden("infirmier_lit_un_bon_200", lire(BONS + "/" + bon + "?centerId=" + CENTRE_A, as(CENTRE_A, "INFIRMIER")));
        golden("infirmier_liste_200", lire(BONS + "?centerId=" + CENTRE_A, as(CENTRE_A, "INFIRMIER")));
        golden("infirmier_consulte_les_verrous_200", lire(DASHBOARD + "/pmp-recalc/locks?centerId=" + CENTRE_A, as(CENTRE_A, "INFIRMIER")));
        for (String role : List.of("MEDECIN", "SECRETAIRE", "DIRECTION")) {
            golden("refus_lecture_" + role.toLowerCase(), lire(BONS + "/" + bon + "?centerId=" + CENTRE_A, as(CENTRE_A, role)));
        }
        golden("refus_anonyme_creation", jours(send(post(java.net.URI.create(BONS)).contentType(MediaType.APPLICATION_JSON)
                .content(bon(CENTRE_A, null, null, null)))));
        etat("roles", CENTRE_A);
    }
}







