package com.hemodialyse.backend.domain.stock.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Aggregated article metrics used by Top N charts.
 */
public record StockTopArticlePoint(
        UUID articleId,
        String code,
        String libelle,
        String unite,
        BigDecimal quantite,
        BigDecimal valeur
) {
}

