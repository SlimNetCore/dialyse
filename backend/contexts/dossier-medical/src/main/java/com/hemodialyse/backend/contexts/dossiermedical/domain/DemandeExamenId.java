package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.UUID;

public record DemandeExamenId(UUID value) {
    public DemandeExamenId {
        if (value == null) throw new IllegalArgumentException("DemandeExamenId requis");
    }
}