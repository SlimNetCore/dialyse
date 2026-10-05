package com.hemodialyse.backend.application.stock;

import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Une séance = un seul bon de sortie « SEANCE » : créé au premier consommable ajouté, puis mis à jour (lignes, lots,
 * mouvements) à chaque ajout, modification ou retrait, sur la vraie base.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class SeanceConsommablesBonIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("77777777-7777-7777-7777-777777777771");
    private static final UUID PATIENT_ID = UUID.fromString("77777777-7777-7777-7777-777777777772");
    private static final UUID SEANCE_ID = UUID.fromString("77777777-7777-7777-7777-777777777773");
    private static final UUID ARTICLE_A = UUID.fromString("77777777-7777-7777-7777-777777777774");
    private static final UUID ARTICLE_B = UUID.fromString("77777777-7777-7777-7777-777777777775");
    private static final UUID LOT_A = UUID.fromString("77777777-7777-7777-7777-777777777776");
    private static final UUID LOT_B = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @Autowired
    private SeanceUseCase seances;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        cleanup();
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                SEANCE_ID, PATIENT_ID, CENTER_ID, LocalDate.of(2026, 10, 4), "VALIDEE", OffsetDateTime.now(ZoneOffset.UTC));
        article(ARTICLE_A, "SCB-A");
        article(ARTICLE_B, "SCB-B");
        lot(LOT_A, ARTICLE_A);
        lot(LOT_B, ARTICLE_B);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM bons_sortie_lignes WHERE bon_sortie_id IN (SELECT id FROM bons_sortie WHERE seance_id = ?)", SEANCE_ID);
        jdbc.update("DELETE FROM bons_sortie WHERE seance_id = ?", SEANCE_ID);
        jdbc.update("DELETE FROM stock_movements WHERE seance_id = ?", SEANCE_ID);
        jdbc.update("DELETE FROM lots WHERE center_id = ?", CENTER_ID);
        jdbc.update("DELETE FROM articles WHERE center_id = ?", CENTER_ID);
        jdbc.update("DELETE FROM seances WHERE id = ?", SEANCE_ID);
    }

    private void article(UUID id, String code) {
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, gere_par_lot, active, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, CENTER_ID, code, "Article " + code, "u", new BigDecimal("10"), BigDecimal.ONE, BigDecimal.TEN, true, true,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void lot(UUID id, UUID articleId) {
        jdbc.update("INSERT INTO lots (id, center_id, article_id, numero_lot, quantite_initiale, quantite_restante, pmp, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, CENTER_ID, articleId, "L-" + id.toString().substring(32), new BigDecimal("10"), new BigDecimal("10"),
                BigDecimal.TEN, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private int bons() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM bons_sortie WHERE seance_id = ?", Integer.class, SEANCE_ID);
    }

    private BigDecimal lignes(UUID articleId) {
        BigDecimal total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantite), 0) FROM bons_sortie_lignes WHERE article_id = ? AND bon_sortie_id IN "
                        + "(SELECT id FROM bons_sortie WHERE seance_id = ?)", BigDecimal.class, articleId, SEANCE_ID);
        return total;
    }

    private BigDecimal restant(UUID lotId) {
        return jdbc.queryForObject("SELECT quantite_restante FROM lots WHERE id = ?", BigDecimal.class, lotId);
    }

    @Test
    void successive_articles_feed_one_single_bon_whose_lines_follow_each_change() {
        CenterId centre = CenterId.of(CENTER_ID);

        seances.addConsommableSeance(centre, SEANCE_ID, ARTICLE_A, BigDecimal.ONE, "inf-01");
        seances.addConsommableSeance(centre, SEANCE_ID, ARTICLE_B, new BigDecimal("2"), "inf-01");
        seances.addConsommableSeance(centre, SEANCE_ID, ARTICLE_A, BigDecimal.ONE, "inf-01");

        assertEquals(1, bons(), "un seul bon de sortie pour la séance");
        assertEquals(0, new BigDecimal("2").compareTo(lignes(ARTICLE_A)));
        assertEquals(0, new BigDecimal("2").compareTo(lignes(ARTICLE_B)));
        assertEquals(0, new BigDecimal("8").compareTo(restant(LOT_A)));
        assertEquals(0, new BigDecimal("8").compareTo(restant(LOT_B)));

        seances.updateConsommableSeance(centre, SEANCE_ID, ARTICLE_A, BigDecimal.ONE, "inf-01");
        assertEquals(1, bons());
        assertEquals(0, BigDecimal.ONE.compareTo(lignes(ARTICLE_A)));
        assertEquals(0, new BigDecimal("9").compareTo(restant(LOT_A)), "le stock en trop est restitué");

        seances.removeConsommableSeance(centre, SEANCE_ID, ARTICLE_B, "inf-01");
        assertEquals(1, bons());
        assertEquals(0, BigDecimal.ZERO.compareTo(lignes(ARTICLE_B)));
        assertEquals(0, BigDecimal.TEN.compareTo(restant(LOT_B)));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_movements WHERE seance_id = ? AND article_id = ?", Integer.class,
                SEANCE_ID, ARTICLE_B), "plus de mouvement pour l'article retiré");
    }
}
