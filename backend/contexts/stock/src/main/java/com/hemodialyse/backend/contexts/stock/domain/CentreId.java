package com.hemodialyse.backend.contexts.stock.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifiant d'un centre (le contexte Stock est strictement isolé par centre).
 */
public record CentreId(UUID valeur) {
    public CentreId {
        Objects.requireNonNull(valeur, "Le centre est obligatoire");
    }
}

