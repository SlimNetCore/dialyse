package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record FacturationSettingsUpdateRequest(
        @NotNull UUID centerId,
        @NotBlank String userId,
        @NotNull BigDecimal tvaRate,
        @NotBlank String codeFormat,
        boolean regroupementMultiForfait
) {
}

