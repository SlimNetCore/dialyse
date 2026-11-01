package com.hemodialyse.backend.contexts.seance.domain;

import java.util.UUID;

public record PecId(UUID valeur) {
    public PecId {
        if (valeur == null) throw new IllegalArgumentException("PecId requis");
    }
}