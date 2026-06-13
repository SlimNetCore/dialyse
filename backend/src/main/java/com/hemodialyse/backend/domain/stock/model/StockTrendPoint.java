package com.hemodialyse.backend.domain.stock.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Daily aggregated stock movement metrics for charting.
 */
public record StockTrendPoint(
        LocalDate date,
        BigDecimal quantite,
        BigDecimal valeur
) {
}

