package com.hemodialyse.backend.contexts.seance.domain;

import java.util.UUID;

public record PatientId(UUID valeur) {
    public PatientId {
        if (valeur == null) throw new IllegalArgumentException("PatientId requis");
    }
}