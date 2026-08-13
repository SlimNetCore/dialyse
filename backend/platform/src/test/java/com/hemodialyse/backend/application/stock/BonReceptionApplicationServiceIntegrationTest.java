package com.hemodialyse.backend.application.stock;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonReception;
import com.hemodialyse.backend.domain.stock.model.BonStatut;
import com.hemodialyse.backend.domain.stock.model.LigneReception;
import com.hemodialyse.backend.domain.stock.port.BonReceptionUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class BonReceptionApplicationServiceIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private BonReceptionUseCase bonReceptionUseCase;

    @Autowired
    private ArticleRepositoryPort articleRepositoryPort;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void valider_should_persist_stock_movement_with_bon_reception_date() {
        UUID articleId = UUID.randomUUID();

        Article article = new Article();
        article.setId(articleId);
        article.setCenterId(CENTER_ID);
        article.setCode("ART-INT-01");
        article.setLibelle("Article integration reception");
        article.setUnite("u");
        article.setStockQuantity(BigDecimal.ZERO);
        article.setSeuilAlerte(new BigDecimal("10"));
        article.setPmpCourant(BigDecimal.ZERO);
        article.setGereParLot(false);
        article.setActive(true);
        article.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        articleRepositoryPort.save(article);

        BonReception created = bonReceptionUseCase.create(
                CenterId.of(CENTER_ID),
                null,
                null,
                LocalDate.of(2026, 8, 2),
                List.of(new LigneReception(UUID.randomUUID(), articleId, new BigDecimal("5"),
                        new BigDecimal("12"), null, null, null, null)),
                "user-int"
        );
        created.setStatut(BonStatut.BROUILLON);
        BonReception validated = bonReceptionUseCase.valider(CenterId.of(CENTER_ID), created.getId(), "user-int");

        assertEquals(LocalDate.of(2026, 8, 2), validated.getDateReception());

        OffsetDateTime movementDate = jdbc.queryForObject(
                "SELECT created_at FROM stock_movements WHERE center_id = ? AND article_id = ? AND mouvement_type = 'ENTREE' ORDER BY created_at DESC LIMIT 1",
                OffsetDateTime.class,
                CENTER_ID,
                articleId
        );

        assertEquals(OffsetDateTime.of(2026, 8, 2, 0, 0, 0, 0, ZoneOffset.UTC), movementDate);
    }
}
