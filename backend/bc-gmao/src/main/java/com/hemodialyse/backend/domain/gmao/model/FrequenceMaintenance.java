package com.hemodialyse.backend.domain.gmao.model;

/**
 * Value Object : Fréquence de maintenance
 */
public enum FrequenceMaintenance {
    HEBDOMADAIRE("Hebdomadaire", 7),
    BIMENSUELLE("Bimensuelle", 15),
    MENSUELLE("Mensuelle", 30),
    TRIMESTRIELLE("Trimestrielle", 90),
    SEMESTRIELLE("Semestrielle", 180),
    ANNUELLE("Annuelle", 365);

    private final String label;
    private final int jours;

    FrequenceMaintenance(String label, int jours) {
        this.label = label;
        this.jours = jours;
    }

    public String getLabel() {
        return label;
    }

    public int getJours() {
        return jours;
    }
}

