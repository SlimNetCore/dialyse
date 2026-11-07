package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Statut clinique d'un antécédent. INACTIF est un état sans transition dédiée (reproduit, KB-MED-65, à confirmer avec les métiers).
 */
public enum StatutClinique {
    ACTIF, RESOLU, INACTIF;

    /** Statut saisi : absent = pas de changement (nul) ; inconnu = saisie invalide. */
    public static StatutClinique lireFacultatif(String saisie) {
        if (saisie == null) return null;
        try {
            return valueOf(saisie);
        } catch (IllegalArgumentException inconnu) {
            throw new SaisieInvalide("Statut clinique inconnu");
        }
    }
}
