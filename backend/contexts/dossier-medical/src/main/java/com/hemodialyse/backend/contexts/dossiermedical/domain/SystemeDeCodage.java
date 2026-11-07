package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Référentiel dans lequel un diagnostic ou une substance est codé. Les noms sont ceux du contrat HTTP (reproduits du legacy :
 * CIM10, LOINC, ATC et LOCAL sont tous acceptés, KB-MED-64, à confirmer avec les métiers).
 */
public enum SystemeDeCodage {
    CIM10, LOINC, ATC, LOCAL;

    /** Lit le système saisi : absent ou inconnu (y compris en minuscules), c'est une saisie invalide (400 sans nom interne, KB-MED-58 et 59). */
    public static SystemeDeCodage lire(String saisie) {
        if (saisie == null) throw new SaisieInvalide("Le système de codage est requis avec un code");
        try {
            return valueOf(saisie);
        } catch (IllegalArgumentException inconnu) {
            throw new SaisieInvalide("Système de codage inconnu");
        }
    }
}
