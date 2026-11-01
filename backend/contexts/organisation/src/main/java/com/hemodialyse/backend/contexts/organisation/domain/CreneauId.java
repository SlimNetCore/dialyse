package com.hemodialyse.backend.contexts.organisation.domain;

import java.util.UUID;

public record CreneauId(UUID valeur) {
    public CreneauId {
        if (valeur == null) throw new IllegalArgumentException("Creneau requis");
    }
}
