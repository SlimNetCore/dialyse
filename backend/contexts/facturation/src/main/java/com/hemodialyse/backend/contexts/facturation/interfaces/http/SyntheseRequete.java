package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Corps de {@code POST /api/v1/facturation/synthese/print} : mêmes champs que le legacy.
 */
public record SyntheseRequete(
        @NotNull UUID centerId,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        String format
) {
}
