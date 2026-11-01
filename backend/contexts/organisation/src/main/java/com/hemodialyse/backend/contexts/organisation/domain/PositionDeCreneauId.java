package com.hemodialyse.backend.contexts.organisation.domain;

import java.util.UUID;

public record PositionDeCreneauId(UUID valeur) {
    public PositionDeCreneauId {
        if (valeur == null) throw new IllegalArgumentException("Position de creneau requise");
    }
}
