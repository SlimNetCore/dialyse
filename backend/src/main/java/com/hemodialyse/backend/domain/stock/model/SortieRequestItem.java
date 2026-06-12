package com.hemodialyse.backend.domain.stock.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Requested article + quantity for a stock exit; the lot is resolved by FEFO.
 */
public record SortieRequestItem(UUID articleId, BigDecimal quantite) {
}

