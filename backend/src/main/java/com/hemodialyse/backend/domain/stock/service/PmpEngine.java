package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.port.StockMovementRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * PMP cascade recalculation engine.
 *
 * <p>On any reception (or correction of a past movement) the whole movement
 * history of the article is replayed, in chronological order, inside a single
 * transaction. Each movement's {@code pmp_apres} is rewritten and the article's
 * current PMP + stock quantity are updated.
 *
 * <p>The ordering relies on the composite index (article_id, created_at) declared
 * on {@code stock_movements}. For very large histories this can be delegated to an
 * asynchronous job (see {@link PmpAsyncRecalcJob}).
 */
@Service
public class PmpEngine {

    private final StockMovementRepositoryPort movementRepo;
    private final ArticleRepositoryPort articleRepo;

    public PmpEngine(StockMovementRepositoryPort movementRepo, ArticleRepositoryPort articleRepo) {
        this.movementRepo = movementRepo;
        this.articleRepo = articleRepo;
    }

    /**
     * Recompute the full PMP cascade for an article and persist results.
     */
    @Transactional
    public PmpCalculator.PmpState recalculerArticle(CenterId centerId, UUID articleId) {
        List<StockMovement> movements = movementRepo.findByArticleOrdered(centerId, articleId);
        PmpCalculator.PmpResult result = PmpCalculator.walk(movements, PmpCalculator.PmpState.empty());

        for (PmpCalculator.PmpStep step : result.steps()) {
            movementRepo.updatePmpApres(step.movement().getId(), step.pmpApres());
        }

        Article article = articleRepo.findById(articleId, centerId).orElse(null);
        if (article != null) {
            article.setPmpCourant(result.finalState().pmp());
            article.setStockQuantity(result.finalState().quantite());
            articleRepo.save(article);
        }
        return result.finalState();
    }
}

