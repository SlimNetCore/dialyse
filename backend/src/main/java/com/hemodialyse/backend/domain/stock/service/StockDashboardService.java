package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.*;
import com.hemodialyse.backend.domain.stock.port.StockDashboardPort;
import com.hemodialyse.backend.domain.stock.port.StockDashboardUseCase;
import com.hemodialyse.backend.domain.stock.port.StockMovementRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StockDashboardService implements StockDashboardUseCase {

    /**
     * Default expiry alert window (days).
     */
    public static final int DEFAULT_EXPIRY_WINDOW_DAYS = 30;

    private static final String METHODE =
            "PMP (Poids Moyen Pondere). A chaque ENTREE : PMP = (valeur du stock + quantite recue x prix unitaire) "
                    + "/ (quantite en stock + quantite recue). Une SORTIE valorise au PMP courant et ne modifie pas le PMP. "
                    + "Toute correction d'une reception passee declenche un recalcul en cascade de tout l'historique.";

    private final StockDashboardPort dashboardPort;
    private final StockMovementRepositoryPort movementRepo;
    private final ArticleRepositoryPort articleRepo;

    public StockDashboardService(StockDashboardPort dashboardPort,
                                 StockMovementRepositoryPort movementRepo,
                                 ArticleRepositoryPort articleRepo) {
        this.dashboardPort = dashboardPort;
        this.movementRepo = movementRepo;
        this.articleRepo = articleRepo;
    }

    @Override
    public List<StockValoriseItem> stockValorise(CenterId centerId) {
        return dashboardPort.stockValorise(centerId);
    }

    @Override
    public List<TracabiliteItem> tracabilite(CenterId centerId, UUID lotId) {
        return dashboardPort.tracabiliteByLot(centerId, lotId);
    }

    @Override
    public List<AlerteStock> alertes(CenterId centerId) {
        return dashboardPort.alertes(centerId, DEFAULT_EXPIRY_WINDOW_DAYS);
    }

    @Override
    public PmpExplanation pmpExplain(CenterId centerId, UUID articleId) {
        Article article = articleRepo.findById(articleId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Article introuvable: " + articleId));

        List<StockMovement> movements = movementRepo.findByArticleOrdered(centerId, articleId);
        List<PmpCalculator.DetailedStep> detailed = PmpCalculator.explain(movements, PmpCalculator.PmpState.empty());

        List<PmpExplanationStep> etapes = new ArrayList<>();
        for (PmpCalculator.DetailedStep d : detailed) {
            StockMovement m = d.movement();
            etapes.add(new PmpExplanationStep(
                    m.getCreatedAt(),
                    m.getMovementType().name(),
                    m.getQuantite(),
                    m.getPrixUnitaire(),
                    d.stateBefore().quantite(),
                    d.stateBefore().valeur(),
                    d.stateBefore().pmp(),
                    d.stateAfter().quantite(),
                    d.stateAfter().valeur(),
                    d.stateAfter().pmp(),
                    formule(d)
            ));
        }

        PmpCalculator.PmpState fin = detailed.isEmpty()
                ? PmpCalculator.PmpState.empty()
                : detailed.get(detailed.size() - 1).stateAfter();

        return new PmpExplanation(
                article.getId(), article.getCode(), article.getLibelle(),
                METHODE, etapes, fin.quantite(), fin.valeur(), fin.pmp());
    }

    private String formule(PmpCalculator.DetailedStep d) {
        StockMovement m = d.movement();
        BigDecimal q = m.getQuantite() != null ? m.getQuantite() : BigDecimal.ZERO;
        boolean entry = m.getMovementType() == StockMovementType.ENTREE
                || (m.getMovementType() == StockMovementType.AJUSTEMENT && m.getPrixUnitaire() != null);

        if (entry) {
            BigDecimal pu = m.getPrixUnitaire() != null ? m.getPrixUnitaire() : BigDecimal.ZERO;
            return String.format(
                    "ENTREE : PMP = (%s + %s x %s) / (%s + %s) = %s / %s = %s",
                    d.stateBefore().valeur().toPlainString(),
                    q.toPlainString(), pu.toPlainString(),
                    d.stateBefore().quantite().toPlainString(), q.toPlainString(),
                    d.stateAfter().valeur().toPlainString(),
                    d.stateAfter().quantite().toPlainString(),
                    d.stateAfter().pmp().toPlainString());
        }
        return String.format(
                "SORTIE : %s unite(s) valorisee(s) au PMP courant %s ; PMP inchange = %s",
                q.toPlainString(), d.stateBefore().pmp().toPlainString(), d.stateAfter().pmp().toPlainString());
    }
}


