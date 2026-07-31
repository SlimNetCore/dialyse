package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateBonReceptionRequest(
        @NotNull UUID centerId,
        UUID bonCommandeId,
        UUID fournisseurId,
        LocalDate dateReception,
        String userId,
        List<BonReceptionLigneDto> lignes
) {
}

