package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FacturationSettingsUpdateRequest(
        @NotNull UUID centerId,
        @NotBlank String userId,
        @NotBlank String codeFormat,
        boolean regroupementMultiForfait
) {
}
