package com.hemodialyse.backend.domain.stock.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Line of a bon de commande (BL).
 */
public record LigneBonCommande(
        UUID id,
        UUID articleId,
        BigDecimal quantite,
        BigDecimal prixUnitaire
) {
    public BigDecimal sousTotal() {
        if (quantite == null || prixUnitaire == null) {
            return BigDecimal.ZERO;
        }
        return quantite.multiply(prixUnitaire);
    }
}

