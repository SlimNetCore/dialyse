package com.hemodialyse.backend.contexts.dossiermedical.domain;

/** Degré de certitude d'une allergie. Une allergie sans statut saisi est SUSPECTEE à la déclaration. */
public enum StatutDeVerification {
    SUSPECTEE, CONFIRMEE, REFUTEE;

    /** Statut saisi : absent = pas de statut (nul) ; inconnu est une saisie invalide (400 sans nom interne). */
    public static StatutDeVerification lireFacultatif(String saisie) {
        if (saisie == null) return null;
        try {
            return valueOf(saisie);
        } catch (IllegalArgumentException inconnu) {
            throw new SaisieInvalide("Statut de vérification inconnu");
        }
    }
}
