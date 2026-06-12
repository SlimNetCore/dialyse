package com.hemodialyse.backend.domain.stock.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Line of a bon de sortie (BS): a quantity taken from a specific lot (FEFO),
 * valued at the lot PMP at exit time.
 */
public record LigneSortie(
        UUID id,
        UUID articleId,
        UUID lotId,
        BigDecimal quantite,
        BigDecimal pmpApplique
) {
    public BigDecimal valeur() {
        if (quantite == null || pmpApplique == null) {
            return BigDecimal.ZERO;
        }
        return quantite.multiply(pmpApplique);
    }
}

