package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.LocalDate;
import java.util.UUID;

public record CreateDecisionRcpRequest(
        UUID centerId,
        LocalDate dateReunion,
        String avis,
        String compteRendu,
        LocalDate prochaineDateRevue
) {
}
