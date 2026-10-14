package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/** Corps de POST /plans-maintenance/{id}/reactiver : contrat inchangé par rapport au legacy. */
public record ReprendrePlanRequete(
        @NotNull(message = "Nouvelle date prévue requise")
        OffsetDateTime nouvelleDatePrevue) {
}
