package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import java.util.UUID;

/** Identité d'une intervention ; les pièces jointes et les lectures la référencent par cette identité seulement. */
public record InterventionId(UUID valeur) {
    public InterventionId {
        if (valeur == null) throw new IllegalArgumentException("Intervention requise");
    }
}
