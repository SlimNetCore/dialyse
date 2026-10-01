package com.hemodialyse.backend.domain.gmao.model;

/**
 * Value Object : Statut de plan de maintenance
 */
public enum StatutPlan {
    ACTIF("Actif"),
    INACTIF("Inactif"),
    ARCHIVE("Archivé");

    private final String label;

    StatutPlan(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

