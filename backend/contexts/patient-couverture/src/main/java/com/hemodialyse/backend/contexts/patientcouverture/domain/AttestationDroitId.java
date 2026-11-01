package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.util.UUID;

public record AttestationDroitId(UUID valeur) {
    public AttestationDroitId {
        if (valeur == null) throw new IllegalArgumentException("AttestationDroitId requis");
    }
}