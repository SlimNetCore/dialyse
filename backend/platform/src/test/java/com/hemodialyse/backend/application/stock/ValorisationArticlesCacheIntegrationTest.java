package com.hemodialyse.backend.application.stock;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.port.StockMovementRepositoryPort;
import com.hemodialyse.backend.domain.stock.service.ValorisationStockCalculator.Valorisation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Cache de la valorisation du stock (AGENTS.md §6) : un deuxième appel identique ne relit pas la base (hit), la clé
 * est propre au centre (aucune fuite inter-centres) et toute écriture de mouvement vide le cache (éviction).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ValorisationArticlesCacheIntegrationTest {

    private static final UUID CENTRE_A = UUID.fromString("99996000-0000-0000-0000-00000000000a");
    private static final UUID CENTRE_B = UUID.fromString("99996000-0000-0000-0000-00000000000b");
    private static final UUID ARTICLE = UUID.fromString("99996000-0000-0000-0000-0000000000a1");
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 1, 10, 8, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime DEBUT = OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime FIN = OffsetDateTime.of(2026, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private ValorisationArticlesService service;
    @Autowired
    private StockMovementRepositoryPort mouvements;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        cleanup();
        cacheManager.getCache(ValorisationArticlesService.CACHE).clear();
        insertArticle(CENTRE_A);
        insertMouvement(CENTRE_A, 10);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM stock_movements WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM articles WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
    }

    @Test
    void a_second_identical_call_is_a_cache_hit_even_if_the_database_changed_behind_its_back() {
        Valorisation premier = service.valoriser(CENTRE_A, Set.of(ARTICLE), DEBUT, FIN).get(ARTICLE);
        assertEquals(0, new BigDecimal("1000").compareTo(premier.valeurFin()));

        insertMouvement(CENTRE_A, 5);   // écriture JDBC directe : aucune éviction
        Valorisation second = service.valoriser(CENTRE_A, Set.of(ARTICLE), DEBUT, FIN).get(ARTICLE);

        assertSame(premier, second);
        assertNotNull(cacheManager.getCache(ValorisationArticlesService.CACHE));
    }

    @Test
    void the_key_is_scoped_to_the_center_so_nothing_leaks_between_centers() {
        service.valoriser(CENTRE_A, Set.of(ARTICLE), DEBUT, FIN);

        Map<UUID, Valorisation> autreCentre = service.valoriser(CENTRE_B, Set.of(ARTICLE), DEBUT, FIN);

        assertEquals(0, BigDecimal.ZERO.compareTo(autreCentre.get(ARTICLE).valeurFin()));
    }

    @Test
    void a_stock_movement_write_evicts_the_cache() {
        Valorisation avant = service.valoriser(CENTRE_A, Set.of(ARTICLE), DEBUT, FIN).get(ARTICLE);
        assertEquals(0, new BigDecimal("1000").compareTo(avant.valeurFin()));

        mouvements.save(StockMovement.entree(CENTRE_A, ARTICLE, UUID.randomUUID(), new BigDecimal("5"),
                new BigDecimal("100"), "test", T0.plusDays(2)));
        Valorisation apres = service.valoriser(CENTRE_A, Set.of(ARTICLE), DEBUT, FIN).get(ARTICLE);

        assertEquals(0, new BigDecimal("1500").compareTo(apres.valeurFin()));
    }

    @Test
    void a_different_period_is_a_different_cache_entry() {
        Valorisation fevrier = service.valoriser(CENTRE_A, Set.of(ARTICLE), DEBUT, FIN).get(ARTICLE);
        Valorisation janvier = service.valoriser(CENTRE_A, Set.of(ARTICLE),
                OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), DEBUT).get(ARTICLE);

        assertEquals(0, new BigDecimal("1000").compareTo(fevrier.valeurFin()));
        assertEquals(0, new BigDecimal("1000").compareTo(janvier.valeurFin()));
        assertEquals(0, new BigDecimal("1000").compareTo(fevrier.valeurDebut()));
        assertEquals(0, BigDecimal.ZERO.compareTo(janvier.valeurDebut()));
    }

    private void insertArticle(UUID centre) {
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, "
                        + "gere_par_lot, active, created_at) VALUES (?, ?, 'VC-1', 'Article cache', 'U', 0, 0, 0, FALSE, TRUE, ?)",
                ARTICLE, centre, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void insertMouvement(UUID centre, int quantite) {
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, mouvement_type, quantite, prix_unitaire, "
                        + "created_by, created_at) VALUES (?, ?, ?, 'ENTREE', ?, 100, 'zt', ?)",
                UUID.randomUUID(), centre, ARTICLE, quantite, T0);
    }
}
