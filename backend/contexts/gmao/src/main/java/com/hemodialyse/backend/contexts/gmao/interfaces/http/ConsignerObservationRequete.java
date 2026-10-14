package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import jakarta.validation.constraints.NotBlank;

/** Corps de POST /equipements/{id}/observations : contrat inchangé par rapport au legacy ({@code observation}). */
public record ConsignerObservationRequete(
        @NotBlank(message = "Observation requise")
        String observation) {
}
