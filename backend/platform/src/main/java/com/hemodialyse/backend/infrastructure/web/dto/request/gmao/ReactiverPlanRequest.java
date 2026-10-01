package com.hemodialyse.backend.infrastructure.web.dto.request.gmao;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * DTO de requête pour réactiver un plan de maintenance
 */
public record ReactiverPlanRequest(
        @NotNull(message = "Nouvelle date prévue requise")
        LocalDateTime nouvelleDatePrevue
) {
}

