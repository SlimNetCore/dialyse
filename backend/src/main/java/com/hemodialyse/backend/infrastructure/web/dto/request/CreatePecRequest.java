package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.LocalDate;
import java.util.UUID;

public record CreatePecRequest(
        UUID patientId, UUID centerId, String userId,
        LocalDate dateDebutDemande, LocalDate dateFinDemande, UUID forfaitDemandeId) {
}

