package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.UUID;

/**
 * Identifiant de l'agrégat {@link Antecedent} (AGENTS.md §14 Identifier).
 */
public record AntecedentId(UUID value) {
    public AntecedentId {
        if (value == null) {
            throw new IllegalArgumentException("AntecedentId requis");
        }
    }
}
