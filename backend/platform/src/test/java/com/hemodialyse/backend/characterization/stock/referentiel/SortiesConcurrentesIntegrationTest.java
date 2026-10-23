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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Concurrence des sorties manuelles (S6, ADR 0043, 0045) : deux sorties simultanées d'un même lot ne dépassent jamais le stock
 * du lot, et une écriture qui tient le verrou de l'article — comme une sortie de séance ou un rejeu du legacy restant (S6b, S7)
 * — donne un 409 {@code STOCK_ECRITURE_CONCURRENTE} sans rien écrire.
 */
class SortiesConcurrentesIntegrationTest extends ReferentielStockCharacterizationSupport {

    @Autowired
    private PlatformTransactionManager transactions;

    @Override
    protected String theme() {
        return "sorties-concurrentes";
    }

    @BeforeEach
    @AfterEach
    void vider() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM bons_sortie_lignes WHERE bon_sortie_id IN (SELECT id FROM bons_sortie WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM bons_sortie WHERE center_id = ?", c);
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", c);
            jdbc.update("DELETE FROM lots WHERE center_id = ?", c);
            jdbc.update("DELETE FROM app_settings WHERE center_id = ? AND cle = 'SEQ_BS'", c);
        }
    }

    private UUID lot(UUID article, String quantite) {
        UUID lot = UUID.randomUUID();
        OffsetDateTime il_y_a_dix_jours = LocalDate.now().minusDays(10).atStartOfDay().atOffset(ZoneOffset.UTC);
        jdbc.update("INSERT INTO lots (id, center_id, article_id, numero_lot, date_peremption, quantite_initiale, quantite_restante, pmp, "
                        + "created_at) VALUES (?, ?, ?, 'L1', ?, ?, ?, 5, ?)", lot, CENTRE_A, article, LocalDate.now().plusDays(30),
                new BigDecimal(quantite), new BigDecimal(quantite), il_y_a_dix_jours);
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, lot_id, mouvement_type, quantite, prix_unitaire, created_by, "
                        + "created_at, saisi_le) VALUES (?, ?, ?, ?, 'ENTREE', ?, 5, 'test', ?, ?)", UUID.randomUUID(), CENTRE_A, article, lot,
                new BigDecimal(quantite), il_y_a_dix_jours, il_y_a_dix_jours.plusHours(1));
        return lot;
    }

    private Resp sortir(UUID article, UUID lot, String quantite) throws Exception {
        String corps = "{\"centerId\":\"" + CENTRE_A + "\",\"dateSortie\":\"" + LocalDate.now().minusDays(1) + "\",\"items\":[{\"articleId\":\""
                + article + "\",\"lotId\":\"" + lot + "\",\"quantite\":" + quantite + "}]}";
        return send(post(java.net.URI.create("/api/v1/stock/bons-sortie")).contentType(MediaType.APPLICATION_JSON).content(corps).with(adminA()));
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
                resultats.add(f.get());
            }
            return resultats;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void deux_sorties_simultanees_d_un_meme_lot_ne_depassent_pas_son_stock() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "10", "5");
        UUID lot = lot(a1, "10");
        List<Resp> reponses = enParallele(List.of(() -> sortir(a1, lot, "6"), () -> sortir(a1, lot, "6")));
        assertEquals(1, reponses.stream().filter(r -> r.status() == 200).count(), reponses.toString());
        assertEquals(1, reponses.stream().filter(r -> r.status() == 422).count(), reponses.toString());
        assertEquals(0, new BigDecimal("4").compareTo(jdbc.queryForObject("SELECT quantite_restante FROM lots WHERE id = ?", BigDecimal.class, lot)));
        assertEquals(0, new BigDecimal("4").compareTo(jdbc.queryForObject("SELECT stock_quantity FROM articles WHERE id = ?", BigDecimal.class, a1)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM bons_sortie WHERE center_id = ?", Integer.class, CENTRE_A));
        assertEquals("BS-00001", jdbc.queryForObject("SELECT reference FROM bons_sortie WHERE center_id = ?", String.class, CENTRE_A));
    }

    @Test
    void une_ecriture_legacy_qui_tient_le_verrou_de_l_article_donne_un_409_et_n_ecrit_rien() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "10", "5");
        UUID lot = lot(a1, "10");
        CountDownLatch verrouPris = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Resp refuse;
        try {
            Future<?> detenteur = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(statut -> {
                jdbc.queryForList("SELECT id FROM articles WHERE id = ? FOR UPDATE", UUID.class, a1);
                verrouPris.countDown();
                try {
                    liberer.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            verrouPris.await();
            refuse = sortir(a1, lot, "3");
            liberer.countDown();
            detenteur.get();
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
        assertEquals(409, refuse.status(), refuse.raw());
        assertEquals("STOCK_ECRITURE_CONCURRENTE", json(refuse).path("code").asString());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bons_sortie WHERE center_id = ?", Integer.class, CENTRE_A));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE center_id = ? AND mouvement_type = 'SORTIE'",
                Integer.class, CENTRE_A));
        assertEquals(0, new BigDecimal("10").compareTo(jdbc.queryForObject("SELECT quantite_restante FROM lots WHERE id = ?", BigDecimal.class, lot)));
        assertEquals(200, sortir(a1, lot, "3").status());
    }
}

