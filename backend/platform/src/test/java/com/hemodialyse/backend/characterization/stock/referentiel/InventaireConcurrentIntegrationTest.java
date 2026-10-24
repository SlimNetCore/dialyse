package com.hemodialyse.backend.characterization.stock.referentiel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Concurrence de l'inventaire (S7, T20 et T21) : deux ouvertures simultanées n'en laissent qu'une ; une écriture de stock commencée
 * avant l'ouverture se termine avant le gel (l'ouverture attend) et figure donc dans le théorique ; une fois l'inventaire ouvert,
 * l'écriture suivante est refusée. Threads et verrous réels (H2 ; le rejeu PostgreSQL 16 est un contrat à part).
 */
class InventaireConcurrentIntegrationTest extends ReferentielStockCharacterizationSupport {

    private static final String INV = "/api/v1/stock/inventaires";

    @Autowired
    private PlatformTransactionManager transactions;

    private static OffsetDateTime minuit(int moinsN) {
        return LocalDate.now().minusDays(moinsN).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    @Override
    protected String theme() {
        return "inventaire-concurrent";
    }

    @BeforeEach
    @AfterEach
    void vider() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM inventaire_lignes WHERE inventaire_id IN (SELECT id FROM inventaires WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM inventaires WHERE center_id = ?", c);
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", c);
            jdbc.update("DELETE FROM lots WHERE center_id = ?", c);
            jdbc.update("DELETE FROM app_settings WHERE center_id = ? AND cle IN ('INV', 'SEQ_INV')", c);
        }
    }

    private UUID lot(UUID article, String numero, String quantite) {
        UUID lot = UUID.randomUUID();
        jdbc.update("INSERT INTO lots (id, center_id, article_id, numero_lot, date_peremption, quantite_initiale, quantite_restante, pmp, "
                        + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 5, ?)", lot, CENTRE_A, article, numero, LocalDate.now().plusDays(30),
                new BigDecimal(quantite), new BigDecimal(quantite), minuit(10));
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, lot_id, mouvement_type, quantite, prix_unitaire, created_by, "
                        + "created_at, saisi_le) VALUES (?, ?, ?, ?, 'ENTREE', ?, 5, 'test', ?, ?)", UUID.randomUUID(), CENTRE_A, article, lot,
                new BigDecimal(quantite), minuit(10), minuit(10).plusHours(1));
        return lot;
    }

    private Resp ouvrir() throws Exception {
        String corps = "{\"dateInventaire\":\"" + LocalDate.now().minusDays(1) + "\"}";
        return send(post(java.net.URI.create(INV)).contentType(MediaType.APPLICATION_JSON).content(corps).with(adminA()));
    }

    private <T> List<T> enParallele(List<Callable<T>> taches) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(taches.size());
        CountDownLatch depart = new CountDownLatch(1);
        try {
            List<Future<T>> futurs = taches.stream().map(t -> pool.submit(() -> {
                depart.await();
                return t.call();
            })).toList();
            depart.countDown();
            List<T> resultats = new ArrayList<>();
            for (Future<T> f : futurs) {
                resultats.add(f.get(30, TimeUnit.SECONDS));
            }
            return resultats;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void deux_ouvertures_simultanees_n_en_laissent_qu_une() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "5", "5");
        lot(a1, "L1", "5");

        List<Resp> reponses = enParallele(List.of(this::ouvrir, this::ouvrir));

        assertEquals(1, reponses.stream().filter(r -> r.status() == 201).count(), reponses.toString());
        List<Resp> refus = reponses.stream().filter(r -> r.status() == 422).toList();
        assertEquals(1, refus.size(), reponses.toString());
        assertEquals("INVENTORY_ALREADY_IN_PROGRESS", json(refus.get(0)).path("code").asString());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inventaires WHERE center_id = ?", Integer.class, CENTRE_A));
        assertEquals("INV-00001", jdbc.queryForObject("SELECT reference FROM inventaires WHERE center_id = ?", String.class, CENTRE_A),
                "le refus ne consomme aucune référence");
    }

    @Test
    void une_ecriture_commencee_avant_l_ouverture_se_termine_avant_le_gel_et_figure_au_theorique() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "5", "5");
        lot(a1, "L1", "5");
        CountDownLatch verrouPris = new CountDownLatch(1);
        CountDownLatch terminer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // « réception » en cours : elle tient le verrou de l'article, puis ajoute un lot et se termine
            Future<?> reception = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(statut -> {
                jdbc.queryForList("SELECT id FROM articles WHERE id = ? FOR UPDATE", UUID.class, a1);
                verrouPris.countDown();
                try {
                    terminer.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                jdbc.update("INSERT INTO lots (id, center_id, article_id, numero_lot, date_peremption, quantite_initiale, "
                                + "quantite_restante, pmp, created_at) VALUES (?, ?, ?, 'L-RECU', ?, 4, 4, 5, ?)", UUID.randomUUID(), CENTRE_A, a1,
                        LocalDate.now().plusDays(60), minuit(2));
                jdbc.update("UPDATE articles SET stock_quantity = stock_quantity + 4 WHERE id = ?", a1);
            }));
            verrouPris.await();
            Future<Resp> ouverture = pool.submit(this::ouvrir);
            Thread.sleep(300);
            assertFalse(ouverture.isDone(), "l'ouverture doit attendre la fin de l'écriture en cours");
            terminer.countDown();
            reception.get(10, TimeUnit.SECONDS);
            Resp ouverte = ouverture.get(30, TimeUnit.SECONDS);

            assertEquals(201, ouverte.status(), ouverte.raw());
            boolean recuAuTheorique = false;
            for (var ligne : json(ouverte).path("details")) {
                if ("L-RECU".equals(ligne.path("numeroLot").asString())) {
                    recuAuTheorique = ligne.path("quantiteTheorique").decimalValue().compareTo(new BigDecimal("4")) == 0;
                }
            }
            assertTrue(recuAuTheorique, "le lot reçu avant le gel figure au théorique : " + ouverte.raw());
        } finally {
            terminer.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void une_ecriture_de_stock_apres_l_ouverture_est_refusee() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "5", "5");
        UUID lot = lot(a1, "L1", "5");
        assertEquals(201, ouvrir().status());

        String corps = "{\"centerId\":\"" + CENTRE_A + "\",\"dateSortie\":\"" + LocalDate.now() + "\",\"items\":[{\"articleId\":\"" + a1
                + "\",\"lotId\":\"" + lot + "\",\"quantite\":1}]}";
        Resp sortie = send(post(java.net.URI.create("/api/v1/stock/bons-sortie")).contentType(MediaType.APPLICATION_JSON).content(corps)
                .with(adminA()));

        assertEquals(422, sortie.status(), sortie.raw());
        assertEquals("STOCK_INVENTORY_IN_PROGRESS", json(sortie).path("code").asString());
        assertEquals(0, new BigDecimal("5").compareTo(jdbc.queryForObject("SELECT quantite_restante FROM lots WHERE id = ?",
                BigDecimal.class, lot)));
    }
}

