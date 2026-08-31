package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record FacturationPreviewUpdateForfaitRequest(
        @NotNull UUID centerId,
        @NotBlank String userId,
        @NotNull UUID forfaitId,
        String month,
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean regroupementMultiForfait
) {
}

