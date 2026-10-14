package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import jakarta.validation.constraints.NotBlank;

/** Corps de POST /interventions/{id}/annuler : contrat inchangé par rapport au legacy. */
public record AnnulerInterventionRequete(
        @NotBlank(message = "Raison requise")
        String raison) {
}
