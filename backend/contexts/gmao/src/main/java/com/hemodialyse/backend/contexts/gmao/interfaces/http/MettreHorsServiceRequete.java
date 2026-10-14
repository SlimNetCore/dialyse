package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import jakarta.validation.constraints.NotBlank;

/** Corps de POST /equipements/{id}/hors-service : contrat inchangé par rapport au legacy ({@code raison}). */
public record MettreHorsServiceRequete(
        @NotBlank(message = "Raison requise")
        String raison) {
}
