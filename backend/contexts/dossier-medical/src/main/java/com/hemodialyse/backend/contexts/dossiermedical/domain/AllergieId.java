package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.UUID;

/**
 * Identifiant de l'agrégat {@link Allergie} (AGENTS.md §14 Identifier).
 */
public record AllergieId(UUID value) {
    public AllergieId {
        if (value == null) {
            throw new IllegalArgumentException("AllergieId requis");
        }
    }
}
