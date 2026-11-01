package com.hemodialyse.backend.contexts.patientcouverture.domain;

public record AssureId(String valeur) {
    public AssureId {
        if (valeur == null || valeur.isBlank()) throw new IllegalArgumentException("Assure requis");
    }
}