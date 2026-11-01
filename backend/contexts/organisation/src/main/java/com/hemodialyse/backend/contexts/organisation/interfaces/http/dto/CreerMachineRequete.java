package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

import java.util.UUID;

/**
 * Référencement d'une machine dans un centre.
 */
public record CreerMachineRequete(UUID centreId, String code, String libelle) {
}
