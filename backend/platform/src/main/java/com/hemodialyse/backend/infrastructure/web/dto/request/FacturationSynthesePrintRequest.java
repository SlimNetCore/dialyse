package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record FacturationSynthesePrintRequest(
        @NotNull UUID centerId,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        String format
) {
}

