package com.hemodialyse.backend.domain.stock.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Line of a bon de reception (BR): an incoming lot for an article.
 */
public record LigneReception(
        UUID id,
        UUID articleId,
        BigDecimal quantite,
        BigDecimal prixUnitaire,
        String numeroLot,
        LocalDate datePeremption,
        UUID emplacementId,
        UUID lotId
) {
    public LigneReception withLotId(UUID newLotId) {
        return new LigneReception(id, articleId, quantite, prixUnitaire, numeroLot, datePeremption, emplacementId, newLotId);
    }
}

