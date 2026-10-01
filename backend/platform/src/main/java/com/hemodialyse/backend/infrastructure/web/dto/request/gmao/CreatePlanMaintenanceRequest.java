package com.hemodialyse.backend.infrastructure.web.dto.request.gmao;

import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO de requête pour créer un plan de maintenance
 */
public record CreatePlanMaintenanceRequest(
        @NotNull(message = "ID équipement requis")
        UUID equipementId,

        @NotBlank(message = "Désignation requise")
        @Size(max = 255, message = "Désignation limitée à 255 caractères")
        String designation,

        String description,

        @NotBlank(message = "Fréquence requise")
        String frequence,

        @NotNull(message = "Prochaine date prévue requise")
        OffsetDateTime prochaineDatePrevue,

        String tachesAEffectuer
) {
}

