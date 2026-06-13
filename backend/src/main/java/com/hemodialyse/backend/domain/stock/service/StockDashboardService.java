package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.*;
import com.hemodialyse.backend.domain.stock.port.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class StockDashboardService implements StockDashboardUseCase {

    /**
     * Default expiry alert window (days).
     */
    public static final int DEFAULT_EXPIRY_WINDOW_DAYS = 30;
    private static final int DEFAULT_ANALYTICS_DAYS = 30;
    private static final int[] ALLOWED_ANALYTICS_DAYS = {7, 30, 90};
    private static final int DEFAULT_TOP_N = 10;
    private static final int MAX_TOP_N = 50;

    private static final String METHODE =
            "PMP (Poids Moyen Pondere). A chaque ENTREE : PMP = (valeur du stock + quantite recue x prix unitaire) "
                    + "/ (quantite en stock + quantite recue). Une SORTIE valorise au PMP courant et ne modifie pas le PMP. "
                    + "Toute correction d'une reception passee declenche un recalcul en cascade de tout l'historique.";

    private final StockDashboardPort dashboardPort;
    private final StockMovementRepositoryPort movementRepo;
    private final ArticleRepositoryPort articleRepo;
    private final LotRepositoryPort lotRepo;
    private final BonReceptionRepositoryPort bonReceptionRepo;
    private final BonSortieRepositoryPort bonSortieRepo;

    public StockDashboardService(StockDashboardPort dashboardPort,
                                 StockMovementRepositoryPort movementRepo,
                                 ArticleRepositoryPort articleRepo,
                                 LotRepositoryPort lotRepo,
                                 BonReceptionRepositoryPort bonReceptionRepo,
                                 BonSortieRepositoryPort bonSortieRepo) {
        this.dashboardPort = dashboardPort;
        this.movementRepo = movementRepo;
        this.articleRepo = articleRepo;
        this.lotRepo = lotRepo;
        this.bonReceptionRepo = bonReceptionRepo;
        this.bonSortieRepo = bonSortieRepo;
    }

    @Override
    public StockDashboardAnalytics analytics(CenterId centerId, int days, int topN, StockTopSort sortBy) {
        int safeDays = sanitizeDays(days);
        int safeTopN = sanitizeTopN(topN);
        StockTopSort safeSort = sortBy != null ? sortBy : StockTopSort.VALUE;

        List<StockValoriseItem> stock = dashboardPort.stockValorise(centerId);
        BigDecimal quantiteTotale = stock.stream()
                .map(StockValoriseItem::quantite)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal valeurTotale = stock.stream()
                .map(StockValoriseItem::valeur)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<StockTrendPoint> trend = fillMissingDays(
                dashboardPort.mouvementTrend(centerId, safeDays),
                safeDays
        );
        List<StockTopArticlePoint> topArticles = dashboardPort.topArticlesByMovement(centerId, safeDays, safeTopN, safeSort);

        return new StockDashboardAnalytics(
                safeDays,
                safeTopN,
                safeSort,
                quantiteTotale,
                valeurTotale,
                trend,
                topArticles
        );
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
        Map<UUID, String> pieceByLotCache = new HashMap<>();
        Map<String, String> pieceBySortieKeyCache = new HashMap<>();

        List<PmpExplanationStep> etapes = new ArrayList<>();
        for (PmpCalculator.DetailedStep d : detailed) {
            StockMovement m = d.movement();
            String piece = pieceCodeFromMovement(centerId, m, pieceByLotCache, pieceBySortieKeyCache);
            String datePiece = m.getCreatedAt() != null ? m.getCreatedAt().toLocalDate().toString() : null;
            etapes.add(new PmpExplanationStep(
                    m.getCreatedAt(),
                    piece,
                    datePiece,
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

    private int sanitizeDays(int days) {
        for (int allowed : ALLOWED_ANALYTICS_DAYS) {
            if (allowed == days) {
                return days;
            }
        }
        return DEFAULT_ANALYTICS_DAYS;
    }

    private int sanitizeTopN(int topN) {
        if (topN <= 0) {
            return DEFAULT_TOP_N;
        }
        return Math.min(topN, MAX_TOP_N);
    }

    private List<StockTrendPoint> fillMissingDays(List<StockTrendPoint> trend, int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        Map<LocalDate, StockTrendPoint> byDate = trend.stream()
                .collect(HashMap::new, (map, item) -> map.put(item.date(), item), HashMap::putAll);

        List<StockTrendPoint> normalized = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate date = start.plusDays(i);
            StockTrendPoint point = byDate.get(date);
            if (point == null) {
                normalized.add(new StockTrendPoint(date, BigDecimal.ZERO, BigDecimal.ZERO));
            } else {
                normalized.add(point);
            }
        }
        return normalized;
    }

    private String pieceCodeFromMovement(CenterId centerId,
                                         StockMovement m,
                                         Map<UUID, String> pieceByLotCache,
                                         Map<String, String> pieceBySortieKeyCache) {
        if (m.getMovementType() == StockMovementType.ENTREE) {
            UUID lotId = m.getLotId();
            if (lotId == null) {
                return "BR";
            }
            return pieceByLotCache.computeIfAbsent(lotId, id -> lotRepo.findById(id, centerId)
                    .flatMap(l -> bonReceptionRepo.findById(l.getBonReceptionId(), centerId))
                    .map(BonReception::getReference)
                    .orElse("BR"));
        }
        if (m.getMovementType() == StockMovementType.SORTIE) {
            String key = (m.getSeanceId() != null ? m.getSeanceId().toString() : "-") + ":"
                    + (m.getLotId() != null ? m.getLotId().toString() : "-") + ":"
                    + (m.getArticleId() != null ? m.getArticleId().toString() : "-");
            return pieceBySortieKeyCache.computeIfAbsent(key, k -> {
                if (m.getSeanceId() == null) {
                    return "BS";
                }
                return bonSortieRepo.findBySeance(m.getSeanceId(), centerId).stream()
                        .filter(bs -> bs.getLignes().stream().anyMatch(l ->
                                (l.lotId() != null && l.lotId().equals(m.getLotId()))
                                        && l.articleId().equals(m.getArticleId())))
                        .map(BonSortie::getReference)
                        .findFirst()
                        .orElse("BS");
            });
        }
        return "AJUST";
    }
}


