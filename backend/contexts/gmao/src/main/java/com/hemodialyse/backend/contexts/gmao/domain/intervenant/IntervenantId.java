package com.hemodialyse.backend.contexts.gmao.domain.intervenant;

import java.util.UUID;

public record IntervenantId(UUID valeur) {
    public IntervenantId {
        if (valeur == null) throw new IllegalArgumentException("Intervenant requis");
    }
}
