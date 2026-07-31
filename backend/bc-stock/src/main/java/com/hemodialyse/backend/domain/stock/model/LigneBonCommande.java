package com.hemodialyse.backend.domain.stock.model;

import com.hemodialyse.backend.domain.shared.vo.Money;
import com.hemodialyse.backend.domain.shared.vo.Quantite;

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
    /**
     * Line subtotal computed through the {@link Money}/{@link Quantite} value objects
     * (Shared Kernel). Returns {@link BigDecimal#ZERO} when either operand is absent
     * to preserve the previous tolerant behaviour on draft lines.
     */
    public BigDecimal sousTotal() {
        if (quantite == null || prixUnitaire == null) {
            return BigDecimal.ZERO;
        }
        return Money.of(prixUnitaire).times(Quantite.of(quantite)).amount();
    }
}

