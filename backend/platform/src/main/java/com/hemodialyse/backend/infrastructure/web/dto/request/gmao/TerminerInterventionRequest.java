package com.hemodialyse.backend.infrastructure.web.dto.request.gmao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * DTO de requête pour terminer une intervention
 */
public record TerminerInterventionRequest(
        @NotBlank(message = "Actions requises")
        String actions,

        @NotBlank(message = "État de l'équipement après intervention requis")
        String etatEquipementApres,

        @NotNull(message = "Date et heure de fin requises")
        OffsetDateTime dateFin,

        /** Cause de la panne trouvée à la clôture, optionnelle. */
        String cause
) {
    public TerminerInterventionRequest(String actions, String etatEquipementApres, OffsetDateTime dateFin) {
        this(actions, etatEquipementApres, dateFin, null);
    }
}

