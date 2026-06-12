package com.hemodialyse.backend.domain.stock.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read model: a consolidated stock alert (expiry, rupture/low stock).
 */
public record AlerteStock(
        String type,        // PEREMPTION | RUPTURE | SEUIL
        UUID articleId,
        String articleLibelle,
        UUID lotId,
        String numeroLot,
        LocalDate datePeremption,
        BigDecimal quantite,
        BigDecimal seuil
) {
}

