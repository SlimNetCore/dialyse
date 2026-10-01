package com.hemodialyse.backend.domain.gmao.model;

/**
 * Value Object : Statut de l'équipement
 */
public enum StatutEquipement {
    EN_SERVICE("En service"),
    EN_MAINTENANCE("En maintenance"),
    EN_ATTENTE_PIECE("En attente de pièce"),
    HORS_SERVICE("Hors service"),
    DESACTIF("Désactivé"),
    REFORME("Réformé");

    private final String label;

    StatutEquipement(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

