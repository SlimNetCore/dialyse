package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

public record UserSearchRequest(
        UUID centerId,
        @Min(0) int page,
        @Min(1) @Max(200) int size,
        String search,
        String username,
        String fullName,
        String email,
        String roles,
        String centers,
        Boolean active
) {
}

