package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.LocalDate;
import java.util.UUID;

public record CreateAttestationRequest(UUID patientId, UUID centerId, LocalDate dateDebut, LocalDate dateFin) {
}

