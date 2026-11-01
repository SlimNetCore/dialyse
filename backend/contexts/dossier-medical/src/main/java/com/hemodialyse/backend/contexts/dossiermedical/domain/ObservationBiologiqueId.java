package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.UUID;

public record ObservationBiologiqueId(UUID value) {
    public ObservationBiologiqueId {
        if (value == null) throw new IllegalArgumentException("ObservationBiologiqueId requis");
    }
}
