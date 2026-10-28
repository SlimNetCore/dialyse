package com.hemodialyse.backend.contexts.sharedkernel.domain;

import java.util.UUID;

/**
 * Identité d'une société (groupe de centres). Shared kernel minimal (ADR 0010).
 */
public record SocieteId(UUID valeur) {
    public SocieteId {
        if (valeur == null) throw new IllegalArgumentException("Société requise");
    }
}
