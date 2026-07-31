package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BonReceptionLigneDto(
        UUID id,
        UUID articleId,
        BigDecimal quantite,
        BigDecimal prixUnitaire,
        String numeroLot,
        LocalDate datePeremption,
        UUID emplacementId
) {
}

