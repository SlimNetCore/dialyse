package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record PatientStatsSearchRequest(
        @NotNull UUID centerId,
        @NotNull UUID patientId,
        LocalDate dateFrom,
        LocalDate dateTo,
        String type,
        String format
) {
}

