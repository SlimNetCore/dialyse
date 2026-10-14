package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/** Corps de POST /interventions/{id}/terminer : contrat inchangé par rapport au legacy. */
public record TerminerInterventionRequete(
        @NotBlank(message = "Actions requises")
        String actions,

        @NotBlank(message = "État de l'équipement après intervention requis")
        String etatEquipementApres,

        @NotNull(message = "Date et heure de fin requises")
        OffsetDateTime dateFin,

        /** Cause de la panne trouvée à la clôture, optionnelle. */
        String cause) {
}
