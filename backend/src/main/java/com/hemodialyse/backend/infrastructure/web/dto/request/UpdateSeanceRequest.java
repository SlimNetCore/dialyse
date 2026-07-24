package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateSeanceRequest(
        @NotNull UUID centerId,
        LocalDate dateSeance
) {
}

