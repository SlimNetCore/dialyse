package com.hemodialyse.backend.domain.gmao.model;

/**
 * Value Object : Statut d'intervention
 */
public enum StatutIntervention {
    PLANIFIEE("Planifiée"),
    EN_COURS("En cours"),
    TERMINEE("Terminée"),
    ANNULEE("Annulée"),
    EN_ATTENTE_VALIDATION("En attente de validation");

    private final String label;

    StatutIntervention(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

