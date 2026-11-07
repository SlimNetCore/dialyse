package com.hemodialyse.backend.contexts.dossiermedical.domain;

/** Nature de la substance d'une allergie. Liste fermée du contrat HTTP (reproduit). */
public enum CategorieDAllergie {
    MEDICAMENT, ALIMENT, ENVIRONNEMENT, BIOLOGIQUE;

    /** Lit la catégorie saisie : absente ou inconnue (y compris en minuscules) est une saisie invalide (400 sans nom interne). */
    public static CategorieDAllergie lire(String saisie) {
        if (saisie == null) throw new SaisieInvalide("La catégorie de l'allergie est requise");
        try {
            return valueOf(saisie);
        } catch (IllegalArgumentException inconnue) {
            throw new SaisieInvalide("Catégorie d'allergie inconnue");
        }
    }
}
