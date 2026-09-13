package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.LocalDate;
import java.util.UUID;

public record ResoudreAntecedentRequest(
        UUID centerId,
        LocalDate dateResolution
) {
}
