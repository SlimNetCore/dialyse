package com.hemodialyse.backend.infrastructure.web.dto.request.gmao;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO de requête pour ajouter une observation à un équipement
 */
public record AjouterObservationRequest(
        @NotBlank(message = "Observation requise")
        String observation
) {
}

