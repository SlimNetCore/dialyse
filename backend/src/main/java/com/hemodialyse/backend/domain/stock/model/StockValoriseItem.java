package com.hemodialyse.backend.domain.stock.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read model: valued stock position for an article.
 */
public record StockValoriseItem(
        UUID articleId,
        String code,
        String libelle,
        String unite,
        BigDecimal quantite,
        BigDecimal pmpCourant,
        BigDecimal valeur
) {
}

