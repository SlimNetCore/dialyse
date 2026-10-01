package com.hemodialyse.backend.infrastructure.web.dto.request.gmao;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO de requête pour terminer une intervention
 */
public record TerminerInterventionRequest(
        @NotBlank(message = "Actions requises")
        String actions,

        @NotBlank(message = "État de l'équipement après intervention requis")
        String etatEquipementApres
) {
}

