package com.hemodialyse.backend.contexts.organisation.domain;

import java.util.UUID;

public record MachineId(UUID valeur) {
    public MachineId {
        if (valeur == null) throw new IllegalArgumentException("Machine requise");
    }
}
