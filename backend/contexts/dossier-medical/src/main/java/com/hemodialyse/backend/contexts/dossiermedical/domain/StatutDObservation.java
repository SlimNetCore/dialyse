package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Statut d'une observation biologique (liste fermée du contrat HTTP, reproduite du legacy). Une observation saisie sans statut est
 * FINAL ; toute correction la passe à CORRIGE (KB-MED-84, reproduit, à confirmer avec les métiers).
 */
public enum StatutDObservation {
    PRELIMINAIRE, FINAL, CORRIGE;

    /** Statut saisi : absent = FINAL à l'enregistrement (nul ici) ; inconnu, vide ou en minuscules = saisie invalide (400 sans nom interne). */
    public static StatutDObservation lireFacultatif(String saisie) {
        if (saisie == null) return null;
        try {
            return valueOf(saisie);
        } catch (IllegalArgumentException inconnu) {
            throw new SaisieInvalide("Statut d'observation inconnu");
        }
    }
}
