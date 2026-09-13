package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.LocalDate;
import java.util.UUID;

public record CreateAllergieRequest(
        UUID centerId,
        String codeSystem,
        String code,
        String codeDisplay,
        String categorie,
        String criticite,
        String typeReaction,
        String manifestations,
        LocalDate dateConstatation,
        String statutVerification
) {
}
