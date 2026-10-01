package com.hemodialyse.backend.infrastructure.web.dto.request.gmao;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO de requête pour annuler une intervention
 */
public record AnnulerInterventionRequest(
        @NotBlank(message = "Raison requise")
        String raison
) {
}

