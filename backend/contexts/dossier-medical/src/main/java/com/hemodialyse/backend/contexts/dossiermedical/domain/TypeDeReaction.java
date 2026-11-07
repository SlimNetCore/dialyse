package com.hemodialyse.backend.contexts.dossiermedical.domain;

/** Allergie vraie ou intolérance. Liste fermée du contrat HTTP (reproduit). */
public enum TypeDeReaction {
    ALLERGIE, INTOLERANCE;

    /** Lit le type de réaction saisi : absent ou inconnu est une saisie invalide (400 sans nom interne). */
    public static TypeDeReaction lire(String saisie) {
        if (saisie == null) throw new SaisieInvalide("Le type de réaction est requis");
        try {
            return valueOf(saisie);
        } catch (IllegalArgumentException inconnu) {
            throw new SaisieInvalide("Type de réaction inconnu");
        }
    }
}
