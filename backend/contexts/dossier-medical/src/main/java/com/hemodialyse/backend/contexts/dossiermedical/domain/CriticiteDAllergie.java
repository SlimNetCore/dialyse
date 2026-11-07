package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Gravité d'une allergie. HAUTE exige des manifestations ; toute allergie HAUTE alimente le bandeau des allergies critiques, y compris
 * une allergie réfutée (reproduit, KB-MED-66, à confirmer avec les métiers et le DPO).
 */
public enum CriticiteDAllergie {
    BASSE, HAUTE;

    public boolean exigeDesManifestations() {
        return this == HAUTE;
    }

    /** Lit la criticité saisie : absente ou inconnue est une saisie invalide (400 sans nom interne). */
    public static CriticiteDAllergie lire(String saisie) {
        if (saisie == null) throw new SaisieInvalide("La criticité de l'allergie est requise");
        try {
            return valueOf(saisie);
        } catch (IllegalArgumentException inconnue) {
            throw new SaisieInvalide("Criticité d'allergie inconnue");
        }
    }
}
