package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record FacturationPreviewExcludeSeanceRequest(
        @NotNull UUID centerId,
        @NotBlank String userId,
        String month,
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean regroupementMultiForfait
) {
}

