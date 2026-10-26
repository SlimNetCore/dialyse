package com.hemodialyse.backend.contexts.planification.domain;

/**
 * Qualification du personnel soignant d'un centre (RG-INF-001). Une qualification inconnue est refusée à la saisie.
 */
public enum Qualification {
    INFIRMIER,
    MAJOR,
    AIDE_SOIGNANT;

    /** La qualification saisie, ou {@link SaisieInvalide} si elle est absente ou inconnue. */
    public static Qualification depuisLaSaisie(String saisie) {
        if (saisie == null || saisie.isBlank()) {
            throw new SaisieInvalide("Qualification requise");
        }
        try {
            return valueOf(saisie.trim());
        } catch (IllegalArgumentException e) {
            throw new SaisieInvalide("Qualification inconnue : " + saisie.trim());
        }
    }
}
