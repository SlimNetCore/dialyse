package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import jakarta.validation.constraints.NotBlank;

/** Corps de POST /interventions/{id}/taches (fonctionnalité nouvelle, KB-GMAO-55). */
public record AjouterTacheRequete(
        @NotBlank(message = "Description de la tâche requise")
        String description) {
}
