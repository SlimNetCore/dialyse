package com.hemodialyse.backend.characterization.stock.referentiel;

import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Sortie pour séances de bout en bout (S6b, ADR 0045, T35 à T42) : bascule par centre, enregistrement de la pièce (le stock sort),
 * consommation de séance par le port publié (aucun mouvement de stock), rapprochement (retour en stock), rôles. Avec le paramètre
 * désactivé, la séance garde la sortie immédiate FEFO.
 */
class SortiesPourSeancesIntegrationTest extends ReferentielStockCharacterizationSupport {

    private static final String PIECES = "/api/v1/stock/sorties-pour-seances";
    private static final LocalDate JOUR = LocalDate.now().plusDays(1);

    @Autowired
    private ConsommationsDeSeance consommations;

    @Override
    protected String theme() {
        return "sorties-pour-seances";
    }

    @Autowired
    private com.hemodialyse.backend.contexts.stock.publishedlanguage.OperationsDeStock operations;

    private UUID lot(UUID article, String numero, String quantite, int jours) {
        UUID lot = UUID.randomUUID();
        OffsetDateTime avant = LocalDate.now().minusDays(10).atStartOfDay().atOffset(ZoneOffset.UTC);
        jdbc.update("INSERT INTO lots (id, center_id, article_id, numero_lot, date_peremption, quantite_initiale, quantite_restante, pmp, "
                        + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 5, ?)", lot, CENTRE_A, article, numero, LocalDate.now().plusDays(jours),
                new BigDecimal(quantite), new BigDecimal(quantite), avant);
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, lot_id, mouvement_type, quantite, prix_unitaire, created_by, "
                        + "created_at, saisi_le) VALUES (?, ?, ?, ?, 'ENTREE', ?, 5, 'test', ?, ?)", UUID.randomUUID(), CENTRE_A, article, lot,
                new BigDecimal(quantite), avant, avant.plusHours(1));
        return lot;
    }

    private UUID seance(String statut) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?, ?, ?, ?, ?, ?)", id,
                UUID.randomUUID(), CENTRE_A, JOUR, statut, OffsetDateTime.now());
        return id;
    }

    private Resp ecrire(boolean post, String chemin, String corps, org.springframework.test.web.servlet.request.RequestPostProcessor qui)
            throws Exception {
        var requete = post ? post(java.net.URI.create(chemin)) : put(java.net.URI.create(chemin));
        return send(requete.contentType(MediaType.APPLICATION_JSON).content(corps).with(qui));
    }

    private BigDecimal decimal(String sql, Object... args) {
        return jdbc.queryForObject(sql, BigDecimal.class, args);
    }

    @Test
    void le_parcours_complet_de_la_piece_a_son_rapprochement() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "10", "5");
        UUID l1 = lot(a1, "L1", "10", 30);
        // bascule du centre (réservée à l'administrateur)
        assertEquals(403, ecrire(false, "/api/v1/stock/parametres", json("{'sortiePourSeancesActivee':true}"),
                as(CENTRE_A, "PHARMACIEN")).status());
        assertEquals(200, ecrire(false, "/api/v1/stock/parametres", json("{'sortiePourSeancesActivee':true}"), adminA()).status());

        // enregistrement : le stock sort réellement, mouvement SORTIE daté d'aujourd'hui
        Resp creee = ecrire(true, PIECES, json("{'jour':'" + JOUR + "','lignes':[{'articleId':'" + a1 + "','quantite':8}]}"),
                as(CENTRE_A, "PHARMACIEN"));
        assertEquals(200, creee.status(), creee.raw());
        UUID piece = UUID.fromString(json(creee).path("id").asString());
        assertEquals("SPS-00001", json(creee).path("reference").asString());
        assertEquals(0, new BigDecimal("2").compareTo(decimal("SELECT quantite_restante FROM lots WHERE id = ?", l1)));
        assertEquals(0, new BigDecimal("2").compareTo(decimal("SELECT stock_quantity FROM articles WHERE id = ?", a1)));

        // une deuxième pièce du même périmètre est refusée tant que la première n'est pas rapprochée (T36)
        Resp refusee = ecrire(true, PIECES, json("{'jour':'" + JOUR + "','lignes':[{'articleId':'" + a1 + "','quantite':1}]}"), adminA());
        assertEquals(422, refusee.status());
        assertEquals("PIECE_PRECEDENTE_NON_RAPPROCHEE", json(refusee).path("code").asString());

        // consommation d'une séance : imputée sur la pièce, aucun mouvement de stock (T37)
        UUID seance = seance("VALIDEE");
        int mouvements = jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE center_id = ?", Integer.class, CENTRE_A);
        consommations.ajouter(CENTRE_A, seance, UUID.randomUUID(), JOUR, a1, new BigDecimal("5"), "inf");
        assertEquals(mouvements, jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE center_id = ?", Integer.class, CENTRE_A));
        assertEquals(0, new BigDecimal("2").compareTo(decimal("SELECT quantite_restante FROM lots WHERE id = ?", l1)));
        assertEquals(0, new BigDecimal("5").compareTo(decimal("SELECT SUM(quantite) FROM consommations_de_seance_imputations "
                + "WHERE seance_id = ? AND lot_id = ?", seance, l1)));
        Resp favoris = send(get(java.net.URI.create("/api/v1/stock/sorties-pour-seances/favoris?jour=" + JOUR)).with(as(CENTRE_A, "INFIRMIER")));
        assertEquals(200, favoris.status(), favoris.raw());
        assertEquals(0, new BigDecimal("3").compareTo(json(favoris).get(0).path("reste").decimalValue()));

        // rapprochement : écart en moins (consommé 5, sorti 8) ⇒ motif exigé, puis retour en stock (T40, T41, T42)
        Resp constate = ecrire(true, PIECES + "/" + piece + "/rapprochement", "{}", as(CENTRE_A, "DIRECTEUR_ADMINISTRATIF"));
        assertEquals(200, constate.status(), constate.raw());
        UUID ligne = UUID.fromString(json(constate).path("lignes").get(0).path("id").asString());
        assertEquals(0, new BigDecimal("-3").compareTo(json(constate).path("lignes").get(0).path("ecart").decimalValue()));
        Resp sansMotif = ecrire(true, PIECES + "/" + piece + "/valider", "{}", as(CENTRE_A, "DIRECTEUR_ADMINISTRATIF"));
        assertEquals("ECART_SANS_MOTIF", json(sansMotif).path("code").asString());
        assertEquals(403, ecrire(false, PIECES + "/" + piece + "/lignes/" + ligne + "/motif", json("{'motif':'dose non utilisee'}"),
                as(CENTRE_A, "DIRECTEUR_ADMINISTRATIF")).status());
        assertEquals(200, ecrire(false, PIECES + "/" + piece + "/lignes/" + ligne + "/motif", json("{'motif':'dose non utilisee'}"),
                as(CENTRE_A, "INFIRMIER")).status());
        Resp validee = ecrire(true, PIECES + "/" + piece + "/valider", "{}", as(CENTRE_A, "DIRECTEUR_ADMINISTRATIF"));
        assertEquals(200, validee.status(), validee.raw());
        assertEquals("RAPPROCHEE", json(validee).path("statut").asString());
        assertEquals(0, new BigDecimal("5").compareTo(decimal("SELECT quantite_restante FROM lots WHERE id = ?", l1)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE center_id = ? AND mouvement_type = 'RETOUR'",
                Integer.class, CENTRE_A));
        assertEquals(0, new BigDecimal("5").compareTo(decimal("SELECT stock_quantity FROM articles WHERE id = ?", a1)));

        // la pièce est figée : plus de consommation sur elle (T39), mais une nouvelle pièce est de nouveau possible
        UUID autre = seance("VALIDEE");
        Throwable refus = org.junit.jupiter.api.Assertions.assertThrows(
                com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationRefusee.class,
                () -> consommations.ajouter(CENTRE_A, autre, null, JOUR, a1, BigDecimal.ONE, "inf"));
        assertEquals("PIECE_DEJA_RAPPROCHEE", ((com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationRefusee) refus).code());
        assertEquals(200, ecrire(true, PIECES, json("{'jour':'" + JOUR + "','lignes':[{'articleId':'" + a1 + "','quantite':1}]}"),
                adminA()).status());
    }

    @Test
    void les_roles_d_ecriture_et_de_lecture() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "10", "5");
        lot(a1, "L1", "10", 30);
        String corps = json("{'jour':'" + JOUR + "','lignes':[{'articleId':'" + a1 + "','quantite':1}]}");
        for (String role : List.of("INFIRMIER", "DIRECTEUR_ADMINISTRATIF", "MEDECIN", "SECRETAIRE")) {
            assertEquals(403, ecrire(true, PIECES, corps, as(CENTRE_A, role)).status(), role);
        }
        assertEquals(200, ecrire(true, PIECES, corps, as(CENTRE_A, "PHARMACIEN")).status());
        assertEquals(200, send(get(java.net.URI.create(PIECES)).with(as(CENTRE_A, "DIRECTEUR_ADMINISTRATIF"))).status());
        assertEquals(403, send(get(java.net.URI.create(PIECES)).with(as(CENTRE_A, "MEDECIN"))).status());
        assertEquals(403, send(get(java.net.URI.create(PIECES + "?centerId=" + CENTRE_B)).with(adminA())).status());
    }

    @Test
    void sans_activation_la_seance_garde_la_sortie_immediate_en_fefo() {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "10", "5");
        UUID l1 = lot(a1, "L1", "10", 30);
        UUID seance = seance("VALIDEE");
        consommations.ajouter(CENTRE_A, seance, UUID.randomUUID(), JOUR, a1, new BigDecimal("4"), "inf");
        assertEquals(0, new BigDecimal("6").compareTo(decimal("SELECT quantite_restante FROM lots WHERE id = ?", l1)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE center_id = ? AND mouvement_type = 'SORTIE'",
                Integer.class, CENTRE_A));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM consommations_de_seance WHERE center_id = ?", Integer.class, CENTRE_A));
    }

    @BeforeEach
    @AfterEach
    void vider() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM consommations_de_seance_imputations WHERE seance_id IN (SELECT seance_id FROM consommations_de_seance "
                    + "WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM consommations_de_seance WHERE center_id = ?", c);
            jdbc.update("DELETE FROM sorties_pour_seances_lignes WHERE sortie_id IN (SELECT id FROM sorties_pour_seances WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM sorties_pour_seances WHERE center_id = ?", c);
            jdbc.update("DELETE FROM inventaire_lignes WHERE inventaire_id IN (SELECT id FROM inventaires WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM inventaires WHERE center_id = ?", c);
            jdbc.update("DELETE FROM stock_parametres WHERE center_id = ?", c);
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", c);
            jdbc.update("DELETE FROM lots WHERE center_id = ?", c);
            jdbc.update("DELETE FROM seances WHERE center_id = ?", c);
            jdbc.update("DELETE FROM app_settings WHERE center_id = ? AND cle IN ('SEQ_SPS', 'SEQ_BS')", c);
        }
    }

    @Test
    void l_inventaire_est_refuse_tant_qu_une_sortie_pour_seances_n_est_pas_rapprochee() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "10", "5");
        lot(a1, "L1", "10", 30);
        Resp creee = ecrire(true, PIECES, json("{'jour':'" + JOUR + "','lignes':[{'articleId':'" + a1 + "','quantite':2}]}"), adminA());
        UUID piece = UUID.fromString(json(creee).path("id").asString());
        String ouverture = json("{'centerId':'" + CENTRE_A + "','dateInventaire':'" + LocalDate.now() + "'}");
        Resp refuse = ecrire(true, "/api/v1/stock/inventaires", ouverture, as(CENTRE_A, "PHARMACIEN"));
        assertEquals(422, refuse.status(), refuse.raw());
        assertEquals("INVENTORY_SORTIE_POUR_SEANCES_NON_RAPPROCHEE", json(refuse).path("code").asString());
        // rapprochée (rien consommé : l'écart en moins est justifié puis validé), la garde ne s'applique plus
        Resp constate = ecrire(true, PIECES + "/" + piece + "/rapprochement", "{}", adminA());
        UUID ligne = UUID.fromString(json(constate).path("lignes").get(0).path("id").asString());
        ecrire(false, PIECES + "/" + piece + "/lignes/" + ligne + "/motif", json("{'motif':'non utilise'}"), adminA());
        assertEquals(200, ecrire(true, PIECES + "/" + piece + "/valider", "{}", adminA()).status());
        Resp apres = ecrire(true, "/api/v1/stock/inventaires", ouverture, as(CENTRE_A, "PHARMACIEN"));
        assertFalse(apres.raw().contains("INVENTORY_SORTIE_POUR_SEANCES_NON_RAPPROCHEE"), apres.raw());
    }

    @Test
    void le_directeur_administratif_lit_le_stock_sans_pouvoir_l_ecrire() throws Exception {
        var da = as(CENTRE_A, "DIRECTEUR_ADMINISTRATIF");
        for (String chemin : List.of("/api/v1/stock/dashboard/alertes", "/api/v1/stock/bons-reception", "/api/v1/stock/bons-commande",
                "/api/v1/stock/bons-sortie", "/api/v1/stock/inventaires", "/api/v1/stock/inventaires/etat",
                "/api/v1/stock/referentiel/articles")) {
            assertEquals(200, send(get(java.net.URI.create(chemin)).with(da)).status(), chemin);
        }
        assertEquals(403, ecrire(true, "/api/v1/stock/bons-reception", "{}", da).status());
        assertEquals(403, ecrire(true, "/api/v1/stock/bons-sortie", "{}", da).status());
        assertEquals(403, ecrire(true, "/api/v1/stock/inventaires", "{}", da).status());
    }

    @Test
    void les_retours_en_stock_sont_comptabilises_comme_des_sorties_negatives() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "10", "5");
        lot(a1, "L1", "10", 30);
        UUID piece = UUID.fromString(json(ecrire(true, PIECES, json("{'jour':'" + JOUR + "','lignes':[{'articleId':'" + a1
                + "','quantite':8}]}"), adminA())).path("id").asString());
        UUID seance = seance("VALIDEE");
        UUID patient = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", patient, CENTRE_A, "PAT-S6B", "Dupont", "Jean",
                "M", LocalDate.of(2026, 7, 1), "ASS-S6B", "NON_VACANCIER", OffsetDateTime.now());
        jdbc.update("UPDATE seances SET patient_id = ? WHERE id = ?", patient, seance);
        jdbc.update("UPDATE stock_parametres SET sortie_pour_seances_activee = true WHERE center_id = ?", CENTRE_A);
        ecrire(false, "/api/v1/stock/parametres", json("{'sortiePourSeancesActivee':true}"), adminA());
        consommations.ajouter(CENTRE_A, seance, null, JOUR, a1, new BigDecimal("5"), "inf");
        Resp resume = send(get(java.net.URI.create("/api/v1/seances/" + seance + "?centerId=" + CENTRE_A)).with(as(CENTRE_A, "INFIRMIER")));
        assertEquals(200, resume.status(), resume.raw());
        assertEquals(1, json(resume).path("consommables").size(), resume.raw());
        assertEquals(a1.toString(), json(resume).path("consommables").get(0).path("articleId").asString());
        assertEquals(0, new BigDecimal("5").compareTo(json(resume).path("consommables").get(0).path("quantite").decimalValue()));
        Resp constate = ecrire(true, PIECES + "/" + piece + "/rapprochement", "{}", adminA());
        UUID ligne = UUID.fromString(json(constate).path("lignes").get(0).path("id").asString());
        ecrire(false, PIECES + "/" + piece + "/lignes/" + ligne + "/motif", json("{'motif':'non utilise'}"), adminA());
        assertEquals(200, ecrire(true, PIECES + "/" + piece + "/valider", "{}", adminA()).status());
        LocalDate aujourdHui = LocalDate.now();
        BigDecimal net = operations.sorties(CENTRE_A, aujourdHui, aujourdHui).stream().flatMap(s -> s.lignes().stream())
                .map(m -> m.montant()).reduce(BigDecimal.ZERO, BigDecimal::add);
        // 8 sortis puis 3 retournés au PMP 5 : 40 − 15
        assertEquals(0, new BigDecimal("25").compareTo(net), net.toPlainString());
    }
}
