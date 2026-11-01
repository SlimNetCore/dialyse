package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

import java.util.UUID;

/**
 * Référencement d'un protocole dans un centre.
 */
public record CreerProtocoleRequete(UUID centreId, String code, String libelle) {
}
