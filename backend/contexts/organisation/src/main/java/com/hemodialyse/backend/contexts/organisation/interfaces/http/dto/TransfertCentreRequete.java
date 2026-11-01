package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Corps du transfert d'un centre vers une autre société.
 */
public record TransfertCentreRequete(@NotNull UUID societeCibleId) {
}
