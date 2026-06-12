package com.hemodialyse.backend.domain.stock.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Requested article + lot + quantity for a stock exit.
 */
public record SortieRequestItem(UUID articleId, UUID lotId, BigDecimal quantite) {
}

