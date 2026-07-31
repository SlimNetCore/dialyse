package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record PrescriptionMedicaleSearchRequest(
        @NotNull UUID centerId,
        @NotNull UUID patientId,
        @Min(0) int page,
        @Min(1) @Max(200) int size,
        LocalDate dateFrom,
        LocalDate dateTo
) {
}

