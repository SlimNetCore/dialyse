package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.util.UUID;

public record MouvementPatientId(UUID valeur) {
    public MouvementPatientId {
        if (valeur == null) throw new IllegalArgumentException("Mouvement requis");
    }
}