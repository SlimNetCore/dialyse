package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

/** Corps de POST /interventions/{id}/rectifier : contrat inchangé par rapport au legacy. */
public record RectifierInterventionRequete(
        @NotBlank(message = "Motif de rectification requis")
        String motif,

        /** Nouvelle date et heure de début, si elle doit être corrigée. */
        OffsetDateTime dateDebut) {
}
