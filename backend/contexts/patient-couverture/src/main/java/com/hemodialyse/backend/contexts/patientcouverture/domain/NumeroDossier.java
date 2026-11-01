package com.hemodialyse.backend.contexts.patientcouverture.domain;

public record NumeroDossier(String valeur) {
    public NumeroDossier {
        if (valeur == null || valeur.isBlank()) throw new IllegalArgumentException("Numero dossier requis");
    }
}