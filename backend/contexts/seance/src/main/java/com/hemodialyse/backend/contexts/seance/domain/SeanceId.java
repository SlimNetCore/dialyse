package com.hemodialyse.backend.contexts.seance.domain;

import java.util.UUID;

public record SeanceId(UUID valeur) {
    public SeanceId {
        if (valeur == null) throw new IllegalArgumentException("SeanceId requis");
    }
}