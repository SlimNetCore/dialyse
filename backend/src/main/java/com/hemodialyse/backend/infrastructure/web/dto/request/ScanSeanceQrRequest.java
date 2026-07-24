package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record ScanSeanceQrRequest(
        @NotNull UUID centerId,
        @NotBlank String qrCode,
        LocalDate dateSeance
) {
}

