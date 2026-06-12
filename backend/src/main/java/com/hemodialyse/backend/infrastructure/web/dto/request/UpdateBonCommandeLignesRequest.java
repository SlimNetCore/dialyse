package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UpdateBonCommandeLignesRequest(
        @NotNull UUID centerId,
        List<BonCommandeLigneDto> lignes
) {
}

