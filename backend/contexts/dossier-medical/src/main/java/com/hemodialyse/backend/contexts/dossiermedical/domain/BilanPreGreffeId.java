package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.UUID;

public record BilanPreGreffeId(UUID value) {
    public BilanPreGreffeId {
        if (value == null) throw new IllegalArgumentException("BilanPreGreffeId requis");
    }
}
