package com.hemodialyse.backend.infrastructure.web.dto.request.gmao;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de requête pour créer une intervention GMAO
 */
public record CreateInterventionRequest(
        @NotNull(message = "ID équipement requis")
        UUID equipementId,

        @NotBlank(message = "Type d'intervention requis")
        String type,

        @NotNull(message = "Date de début requise")
        LocalDateTime dateDebut,

        @NotBlank(message = "Description requise")
        String description,

        UUID intervenantId,

        @NotBlank(message = "État de l'équipement requis")
        String etatEquipementAvant
) {
}

