package com.hemodialyse.backend.contexts.gmao.domain;

import java.util.UUID;

/**
 * Identité d'un centre de dialyse. Destiné au shared-kernel cible (ADR 0010) ; copie locale tant que le module
 * {@code contexts/shared-kernel} n'existe pas (voir fiche de slice, points à valider).
 */
public record CentreId(UUID valeur) {
    public CentreId {
        if (valeur == null) throw new IllegalArgumentException("Centre requis");
    }
}
