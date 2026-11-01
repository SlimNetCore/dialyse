package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.UUID;

public record SerologieId(UUID value) {
    public SerologieId {
        if (value == null) throw new IllegalArgumentException("SerologieId requis");
    }
}
