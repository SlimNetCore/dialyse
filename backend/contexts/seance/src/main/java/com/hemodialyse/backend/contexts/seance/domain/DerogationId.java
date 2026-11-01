package com.hemodialyse.backend.contexts.seance.domain;

import java.util.UUID;

public record DerogationId(UUID valeur) {
    public DerogationId {
        if (valeur == null) throw new IllegalArgumentException("DerogationId requis");
    }
}