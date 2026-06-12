package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.LocalDate;
import java.util.UUID;

public record CreateSeanceRequest(
        UUID centerId,
        UUID patientId,
        LocalDate dateSeance
) {
}

