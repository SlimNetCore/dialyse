package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RoleSearchRequest(
        @Min(0) int page,
        @Min(1) @Max(200) int size,
        String search,
        String code,
        String name,
        String description
) {
}

