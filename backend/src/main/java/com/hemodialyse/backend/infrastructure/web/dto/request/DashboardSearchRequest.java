package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DashboardSearchRequest(
        @NotNull UUID centerId,
        Integer expirationDays,
        String month
) {
}
