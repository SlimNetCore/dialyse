package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateEmplacementRequest(
        @NotNull UUID centerId,
        String code,
        @NotBlank String libelle
) {
}

