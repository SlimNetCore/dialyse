package com.hemodialyse.backend.contexts.facturation.domain;

import java.util.UUID;

/**
 * Identité d'un centre de dialyse. Destinée au shared-kernel cible (ADR 0010) ; copie locale tant que le module
 * {@code contexts/shared-kernel} n'existe pas.
 */
public record CentreId(UUID valeur) {
    public CentreId {
        if (valeur == null) throw new IllegalArgumentException("Centre requis");
    }
}
