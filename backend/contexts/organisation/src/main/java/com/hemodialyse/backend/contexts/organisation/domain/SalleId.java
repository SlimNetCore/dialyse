package com.hemodialyse.backend.contexts.organisation.domain;

import java.util.UUID;

public record SalleId(UUID valeur) {
    public SalleId {
        if (valeur == null) throw new IllegalArgumentException("Salle requise");
    }
}
