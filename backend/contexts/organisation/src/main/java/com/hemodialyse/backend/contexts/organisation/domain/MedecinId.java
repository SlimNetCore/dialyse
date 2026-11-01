package com.hemodialyse.backend.contexts.organisation.domain;

import java.util.UUID;

public record MedecinId(UUID valeur) {
    public MedecinId {
        if (valeur == null) throw new IllegalArgumentException("Medecin requis");
    }
}
