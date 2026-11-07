package com.hemodialyse.backend.contexts.dossiermedical.domain;

/** Famille d'un examen demandé. Les noms sont sensibles à la casse (reproduit : {@code biologie} est refusé, KB-MED-42). */
public enum CategorieDExamen {
    BIOLOGIE, IMAGERIE, FONCTIONNEL, ANAPATH;

    /** Lit la catégorie saisie : absente ou inconnue, c'est une saisie invalide (400, le legacy répondait 500 pour l'absente, KB-MED-35). */
    public static CategorieDExamen lire(String saisie) {
        if (saisie == null) throw new SaisieInvalide("La catégorie de l'examen est requise");
        try {
            return valueOf(saisie);
        } catch (IllegalArgumentException inconnue) {
            throw new SaisieInvalide("Catégorie d'examen inconnue");
        }
    }
}
