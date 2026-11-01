package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.time.LocalDate;

public record Identite(String nom, String prenom, String sexe, LocalDate dateNaissance) {
    public Identite {
        if (nom == null || nom.isBlank()) throw new IllegalArgumentException("Nom requis");
        if (prenom == null || prenom.isBlank()) throw new IllegalArgumentException("Prenom requis");
        if (sexe == null || sexe.isBlank()) throw new IllegalArgumentException("Sexe requis");
    }
}