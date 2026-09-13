package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record CorrigerObservationRequest(
        UUID centerId,
        BigDecimal valeurNum,
        String unite,
        String valeurTexte
) {
}
