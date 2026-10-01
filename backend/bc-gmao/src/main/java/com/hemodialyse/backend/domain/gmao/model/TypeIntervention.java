package com.hemodialyse.backend.domain.gmao.model;

/**
 * Value Object : Type d'intervention
 */
public enum TypeIntervention {
    PREVENTIVE("Maintenance préventive"),
    CURATIVE("Maintenance curative"),
    URGENTE("Intervention urgente"),
    CONTROLE("Contrôle technique"),
    INSTALLATION("Installation"),
    DEINSTALLATION("Désinstallation"),
    REMPLACEMENT_PIECE("Remplacement de pièce"),
    REVISION_COMPLETE("Révision complète");

    private final String label;

    TypeIntervention(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

