package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.model.StockMovementType;
import com.hemodialyse.backend.domain.stock.port.StockMovementRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
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
        // Replay the whole history in chronological order (article_id, created_at, id).
        List<StockMovement> movements = movementRepo.findByArticleOrdered(centerId, articleId);
        List<PmpCalculator.DetailedStep> steps =
                PmpCalculator.explain(movements, PmpCalculator.PmpState.empty());

        // Build a single batched update:
        //  - every movement gets its recomputed pmp_apres
        //  - each SORTIE is revalued at the PMP recomputed just before it (stateBefore.pmp())
        List<StockMovementRepositoryPort.MovementRecalc> updates = new ArrayList<>(steps.size());
        PmpCalculator.PmpState finalState = PmpCalculator.PmpState.empty();
        for (PmpCalculator.DetailedStep step : steps) {
            finalState = step.stateAfter();
            BigDecimal pmpApres = step.stateAfter().pmp();
            BigDecimal valorisation = null;
            if (step.movement().getMovementType() == StockMovementType.SORTIE) {
                valorisation = step.stateBefore().pmp();
            }
            updates.add(new StockMovementRepositoryPort.MovementRecalc(
                    step.movement().getId(), pmpApres, valorisation));
        }
        movementRepo.applyRecalc(updates);

        // Recompute the article current PMP + stock quantity from the final replayed state.
        Article article = articleRepo.findById(articleId, centerId).orElse(null);
        if (article != null) {
            article.setPmpCourant(finalState.pmp());
            article.setStockQuantity(finalState.quantite());
            articleRepo.save(article);
        }
        return finalState;
    }
}

