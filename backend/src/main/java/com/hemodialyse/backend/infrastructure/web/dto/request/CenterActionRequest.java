package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Generic body carrying the center (and optionally the acting user) for
 * state-transition endpoints (valider, etc.).
 */
public record CenterActionRequest(
        @NotNull UUID centerId,
        String userId
) {
}

