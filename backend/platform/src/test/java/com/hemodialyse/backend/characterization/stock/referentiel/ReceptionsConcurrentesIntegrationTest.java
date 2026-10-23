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
 * Concurrence des réceptions (S5, ADR 0043, 0045) : verrou de ligne sur les articles par identifiant croissant (aucun
 * interblocage entre réceptions croisées), PMP cohérent après validations simultanées, 409 {@code STOCK_ECRITURE_CONCURRENTE}
 * quand une autre écriture (ici une transaction qui tient le verrou comme le legacy) précède.
 */
class ReceptionsConcurrentesIntegrationTest extends ReferentielStockCharacterizationSupport {

    @Autowired
    private PlatformTransactionManager transactions;

    @Override
    protected String theme() {
        return "receptions-concurrentes";
    }

    @BeforeEach
    @AfterEach
    void vider() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM bons_reception_lignes WHERE bon_reception_id IN (SELECT id FROM bons_reception WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM bons_reception WHERE center_id = ?", c);
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", c);
            jdbc.update("DELETE FROM lots WHERE center_id = ?", c);
            jdbc.update("DELETE FROM app_settings WHERE center_id = ? AND cle = 'SEQ_BR'", c);
        }
    }

    private UUID bon(UUID... articlesEtQuantites) throws Exception {
        StringBuilder lignes = new StringBuilder();
        for (int i = 0; i < articlesEtQuantites.length; i++) {
            lignes.append(i == 0 ? "" : ",").append("{\"articleId\":\"").append(articlesEtQuantites[i]).append("\",\"quantite\":10,\"prixUnitaire\":")
                    .append(2 + i).append("}");
        }
        String corps = "{\"centerId\":\"" + CENTRE_A + "\",\"dateReception\":\"" + LocalDate.now().minusDays(3) + "\",\"lignes\":[" + lignes + "]}";
        Resp r = send(post(java.net.URI.create("/api/v1/stock/bons-reception")).contentType(MediaType.APPLICATION_JSON).content(corps).with(adminA()));
        assertEquals(200, r.status());
        return UUID.fromString(json(r).path("id").asString());
    }

    private Resp valider(UUID bon) throws Exception {
        return send(post(java.net.URI.create("/api/v1/stock/bons-reception/" + bon + "/valider")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"centerId\":\"" + CENTRE_A + "\"}").with(adminA()));
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
            java.util.ArrayList<T> resultats = new java.util.ArrayList<>();
            for (Future<T> f : futurs) {
                resultats.add(f.get());
            }
            return resultats;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void deux_validations_simultanees_du_meme_article_laissent_un_stock_et_un_pmp_coherents() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "0", "0");
        UUID b1 = bon(a1);
        UUID b2 = bon(a1);
        List<Resp> reponses = enParallele(List.of(() -> valider(b1), () -> valider(b2)));
        reponses.forEach(r -> assertEquals(200, r.status(), r.raw()));
        assertEquals(0, new BigDecimal("20").compareTo(jdbc.queryForObject("SELECT stock_quantity FROM articles WHERE id = ?", BigDecimal.class, a1)));
        assertEquals(0, new BigDecimal("2.00").compareTo(jdbc.queryForObject("SELECT pmp_courant FROM articles WHERE id = ?", BigDecimal.class, a1)));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM lots WHERE center_id = ?", Integer.class, CENTRE_A));
    }

    @Test
    void deux_receptions_croisees_sur_les_memes_articles_ne_se_bloquent_pas() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "0", "0");
        UUID a2 = article(CENTRE_A, "ART_2", "A2", "Article 2", null, null, true, null, "0", "0");
        UUID b1 = bon(a1, a2);
        UUID b2 = bon(a2, a1);
        List<Resp> reponses = enParallele(List.of(() -> valider(b1), () -> valider(b2)));
        reponses.forEach(r -> assertEquals(200, r.status(), r.raw()));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE center_id = ?", Integer.class, CENTRE_A));
        assertEquals(0, new BigDecimal("20").compareTo(jdbc.queryForObject("SELECT stock_quantity FROM articles WHERE id = ?", BigDecimal.class, a1)));
        assertEquals(0, new BigDecimal("20").compareTo(jdbc.queryForObject("SELECT stock_quantity FROM articles WHERE id = ?", BigDecimal.class, a2)));
    }

    @Test
    void une_ecriture_qui_tient_le_verrou_de_l_article_donne_un_409_et_n_ecrit_rien() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "0", "0");
        UUID b1 = bon(a1);
        CountDownLatch verrouPris = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
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
            Resp refuse = valider(b1);
            liberer.countDown();
            detenteur.get();
            assertEquals(409, refuse.status(), refuse.raw());
            assertEquals("STOCK_ECRITURE_CONCURRENTE", json(refuse).path("code").asString());
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM lots WHERE center_id = ?", Integer.class, CENTRE_A));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE center_id = ?", Integer.class, CENTRE_A));
        assertEquals("BROUILLON", jdbc.queryForObject("SELECT statut FROM bons_reception WHERE id = ?", String.class, b1));
        assertEquals(200, valider(b1).status());
    }
}

