package com.hemodialyse.backend.domain.gmao.model;

/**
 * Value Object : Statut de tâche
 */
public enum StatutTache {
    A_FAIRE("À faire"),
    EN_COURS("En cours"),
    COMPLETEE("Complétée");

    private final String label;

    StatutTache(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

