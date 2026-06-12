package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FromBonCommandeRequest(
        @NotNull UUID centerId,
        @NotNull UUID bonCommandeId,
        String userId
) {
}

