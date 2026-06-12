package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DocumentSearchRequest(
        @NotNull UUID centerId,
        String typeDocument,
        @Min(0) int page,
        @Min(1) @Max(200) int size
) {
}

