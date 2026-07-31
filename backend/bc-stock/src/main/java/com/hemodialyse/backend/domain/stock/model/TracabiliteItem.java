package com.hemodialyse.backend.domain.stock.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read model: lot -> patient -> seance traceability row (recall support).
 */
public record TracabiliteItem(
        UUID lotId,
        String numeroLot,
        UUID articleId,
        String articleLibelle,
        UUID seanceId,
        UUID patientId,
        LocalDate dateSortie,
        BigDecimal quantite
) {
}

