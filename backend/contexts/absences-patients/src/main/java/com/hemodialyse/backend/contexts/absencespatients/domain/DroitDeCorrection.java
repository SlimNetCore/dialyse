package com.hemodialyse.backend.contexts.absencespatients.domain;

/**
 * Droit de corriger une absence déjà qualifiée, d'en annuler une qualifiée et de rattraper une absence (administrateur et
 * médecin, RG-ABS-020, 022). Calculé à l'interface à partir du rôle et transmis à l'agrégat.
 */
public enum DroitDeCorrection {
    ACCORDE,
    REFUSE;

    public boolean accorde() {
        return this == ACCORDE;
    }
}
