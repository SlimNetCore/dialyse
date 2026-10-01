package com.hemodialyse.backend.infrastructure.web.dto.request.gmao;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO de requête pour marquer un équipement comme hors service
 */
public record MarquerHorsServiceRequest(
        @NotBlank(message = "Raison requise")
        String raison
) {
}

