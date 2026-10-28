package com.hemodialyse.backend.contexts.sharedkernel.domain;

import java.util.UUID;

/**
 * Identité d'un centre de dialyse (shared kernel minimal, ADR 0010). Copie unique partagée par tous les contextes extraits ;
 * la règle d'isolement par centre (RG-TRV-002) s'appuie sur ce type.
 */
public record CentreId(UUID valeur) {
    public CentreId {
        if (valeur == null) throw new IllegalArgumentException("Centre requis");
    }
}
