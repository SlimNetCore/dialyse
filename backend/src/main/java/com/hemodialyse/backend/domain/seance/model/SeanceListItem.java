package com.hemodialyse.backend.domain.seance.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SeanceListItem(
        UUID id,
        UUID centerId,
        UUID patientId,
        String patientCode,
        String patientNom,
        String patientPrenom,
        LocalDate dateSeance,
        SeanceStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime validatedAt,
        OffsetDateTime signedByInfirmierAt,
        OffsetDateTime signedByMedecinAt
) {
}

