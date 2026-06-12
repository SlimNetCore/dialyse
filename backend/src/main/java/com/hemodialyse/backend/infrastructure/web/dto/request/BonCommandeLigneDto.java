package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record BonCommandeLigneDto(
        UUID id,
        UUID articleId,
        BigDecimal quantite,
        BigDecimal prixUnitaire
) {
}

