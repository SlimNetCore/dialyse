package com.hemodialyse.backend.infrastructure.web.dto.request.gmao;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

/**
 * DTO de requête pour rectifier (rouvrir) une intervention terminée.
 */
public record RectifierInterventionRequest(
        @NotBlank(message = "Motif de rectification requis")
        String motif,

        /** Nouvelle date et heure de début, si elle doit être corrigée. */
        OffsetDateTime dateDebut
) {
}
