package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record FacturationPreviewRequest(
        @NotNull UUID centerId,
        String month,
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean regroupementMultiForfait
) {
}

