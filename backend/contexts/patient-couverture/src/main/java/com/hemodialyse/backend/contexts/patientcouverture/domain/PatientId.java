package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.util.UUID;

public record PatientId(UUID valeur) {
    public PatientId {
        if (valeur == null) throw new IllegalArgumentException("Patient requis");
    }
}