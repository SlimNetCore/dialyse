package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.UUID;

public record ConstanteId(UUID value) {
    public ConstanteId {
        if (value == null) throw new IllegalArgumentException("ConstanteId requis");
    }
}