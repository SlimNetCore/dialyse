package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateBonCommandeRequest(
        @NotNull UUID centerId,
        UUID fournisseurId,
        String userId,
        List<BonCommandeLigneDto> lignes
) {
}

