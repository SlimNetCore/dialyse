package com.hemodialyse.backend.contexts.organisation.domain;

import java.util.UUID;

public record ProtocoleId(UUID valeur) {
    public ProtocoleId {
        if (valeur == null) throw new IllegalArgumentException("Protocole requis");
    }
}
