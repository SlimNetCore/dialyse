package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.LocalDate;
import java.util.UUID;

public record ValidatePecRequest(
        UUID centerId, String userId,
        LocalDate dateDebutEffectif, LocalDate dateFinEffectif, UUID forfaitEffectifId) {
}

