package com.hemodialyse.backend.domain.stock.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregated payload for V2 stock dashboard charts and filters.
 */
public record StockDashboardAnalytics(
        int days,
        int topN,
        StockTopSort sortBy,
        BigDecimal stockQuantiteTotale,
        BigDecimal stockValeurTotale,
        List<StockTrendPoint> trend,
        List<StockTopArticlePoint> topArticles
) {
}

