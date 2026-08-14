package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RegisterFacturePaymentRequest(
        @NotNull UUID centerId,
        @NotNull BigDecimal montant,
        LocalDate dateReglement,
        String userId
) {
}
