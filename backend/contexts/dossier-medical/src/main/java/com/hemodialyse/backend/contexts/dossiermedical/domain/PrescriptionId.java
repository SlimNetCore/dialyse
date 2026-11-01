package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.UUID;

public record PrescriptionId(UUID value) {
    public PrescriptionId {
        if (value == null) throw new IllegalArgumentException("PrescriptionId requis");
    }
}