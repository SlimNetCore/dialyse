package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.application.stock.GroupeArticleApplicationService;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.GroupeArticle;
import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.port.GroupeArticleRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockMovementHistoryPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Groupes d'articles : persistance, unicité du nom par centre, isolation multi-centre, et lecture de l'historique
 * complet des mouvements (y compris ceux clôturés par un inventaire).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class GroupeArticleIntegrationTest {

    private static final UUID CENTRE_A = UUID.fromString("99995000-0000-0000-0000-00000000000a");
    private static final UUID CENTRE_B = UUID.fromString("99995000-0000-0000-0000-00000000000b");
    private static final UUID ARTICLE_A = UUID.fromString("99995000-0000-0000-0000-0000000000a1");
    private static final UUID ARTICLE_B = UUID.fromString("99995000-0000-0000-0000-0000000000b1");

    @Autowired
    private GroupeArticleApplicationService service;
    @Autowired
    private GroupeArticleRepositoryPort groupes;
    @Autowired
    private StockMovementHistoryPort historique;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        cleanup();
        insertArticle(ARTICLE_A, CENTRE_A, "GA-A");
        insertArticle(ARTICLE_B, CENTRE_B, "GA-B");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM groupes_articles_items WHERE groupe_id IN (SELECT id FROM groupes_articles WHERE center_id IN (?, ?))",
                CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM groupes_articles WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM stock_movements WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM articles WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
    }

    @Test
    void a_group_should_round_trip_and_be_listed_in_pages_for_its_center_only() {
        GroupeArticle kit = service.creer(CENTRE_A, "Kit CNAS", "Kit de dialyse", List.of(ARTICLE_A));

        GroupeArticle relu = service.obtenir(CENTRE_A, kit.id());
        PagedResult<GroupeArticle> pageA = service.lister(CENTRE_A, 0, 20);
        PagedResult<GroupeArticle> pageB = service.lister(CENTRE_B, 0, 20);

        assertEquals("Kit CNAS", relu.nom());
        assertEquals(List.of(ARTICLE_A), List.copyOf(relu.articleIds()));
        assertEquals(1, pageA.total());
        assertEquals(0, pageB.total());
        assertThrows(BusinessException.class, () -> service.obtenir(CENTRE_B, kit.id()));
    }

    @Test
    void the_name_should_be_unique_per_center_but_reusable_in_another_center() {
        service.creer(CENTRE_A, "Kit CNAS", null, List.of(ARTICLE_A));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.creer(CENTRE_A, "  KIT   cnas ", null, List.of(ARTICLE_A)));
        GroupeArticle autreCentre = service.creer(CENTRE_B, "Kit CNAS", null, List.of(ARTICLE_B));

        assertEquals("GROUPE_ARTICLE_NOM_EXISTANT", e.getCode());
        assertEquals(CENTRE_B, autreCentre.centerId());
        assertTrue(groupes.existsByCle(CENTRE_A, "kit cnas", null));
    }

    @Test
    void a_group_cannot_contain_an_article_of_another_center() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.creer(CENTRE_A, "Kit", null, List.of(ARTICLE_A, ARTICLE_B)));

        assertEquals("GROUPE_ARTICLE_ARTICLE_INCONNU", e.getCode());
    }

    @Test
    void update_and_delete_should_be_scoped_to_the_center() {
        GroupeArticle kit = service.creer(CENTRE_A, "Kit", null, List.of(ARTICLE_A));

        GroupeArticle modifie = service.modifier(CENTRE_A, kit.id(), "Kit CNAS", "desc", List.of(ARTICLE_A));
        assertEquals("Kit CNAS", service.obtenir(CENTRE_A, kit.id()).nom());
        assertEquals(kit.id(), modifie.id());

        assertThrows(BusinessException.class, () -> service.supprimer(CENTRE_B, kit.id()));
        service.supprimer(CENTRE_A, kit.id());
        assertFalse(groupes.findById(CENTRE_A, kit.id()).isPresent());
    }

    @Test
    void the_company_view_should_read_the_groups_of_the_given_centers_only() {
        service.creer(CENTRE_A, "Kit CNAS", null, List.of(ARTICLE_A));
        service.creer(CENTRE_B, "Kit CNAS", null, List.of(ARTICLE_B));

        assertEquals(1, groupes.findByCenters(List.of(CENTRE_A)).size());
        assertEquals(2, groupes.findByCenters(List.of(CENTRE_A, CENTRE_B)).size());
        assertTrue(groupes.findByCenters(List.of()).isEmpty());
    }

    @Test
    void history_should_include_movements_closed_by_an_inventory_and_stay_in_the_center() {
        OffsetDateTime t = OffsetDateTime.of(2026, 1, 10, 8, 0, 0, 0, ZoneOffset.UTC);
        insertMouvement(CENTRE_A, ARTICLE_A, "ENTREE", 10, t, null);
        insertMouvement(CENTRE_A, ARTICLE_A, "SORTIE", 2, t.plusDays(1), UUID.randomUUID());
        insertMouvement(CENTRE_B, ARTICLE_B, "ENTREE", 7, t, null);

        Map<UUID, List<StockMovement>> mvts = historique.mouvementsParArticle(
                CenterId.of(CENTRE_A), List.of(ARTICLE_A, ARTICLE_B));

        assertEquals(2, mvts.get(ARTICLE_A).size());
        assertEquals(t.toInstant(), mvts.get(ARTICLE_A).get(0).getCreatedAt().toInstant());
        assertFalse(mvts.containsKey(ARTICLE_B));
        assertTrue(historique.mouvementsParArticle(CenterId.of(CENTRE_A), List.of()).isEmpty());
    }

    private void insertArticle(UUID id, UUID centre, String code) {
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, "
                        + "gere_par_lot, active, created_at) VALUES (?, ?, ?, ?, 'U', 0, 0, 0, FALSE, TRUE, ?)",
                id, centre, code, "Article " + code, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void insertMouvement(UUID centre, UUID article, String type, int q, OffsetDateTime at, UUID inventaireId) {
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, mouvement_type, quantite, prix_unitaire, "
                        + "created_by, created_at, inventaire_id) VALUES (?, ?, ?, ?, ?, 100, 'zt', ?, ?)",
                UUID.randomUUID(), centre, article, type, q, at, inventaireId);
    }
}
