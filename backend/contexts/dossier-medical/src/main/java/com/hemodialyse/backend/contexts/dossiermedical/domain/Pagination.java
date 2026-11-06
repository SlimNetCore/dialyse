package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Page demandée de l'historique : {@code page} à partir de 0, {@code taille} de 1 à 100. Hors borne : 400 (le legacy refusait déjà
 * {@code page=-1}, {@code size=0} et {@code size=-1} ; le plafond de 100 est l'écart décidé du 06/11, KB-MED-06, comme PC1 et la
 * Planification : {@code size=1000} était accepté).
 */
public record Pagination(int page, int taille) {
    public static final int TAILLE_MAXIMALE = 100;

    public Pagination {
        if (page < 0) throw new SaisieInvalide("Le numéro de page ne peut pas être négatif");
        if (taille < 1 || taille > TAILLE_MAXIMALE) throw new SaisieInvalide("La taille de page doit être comprise entre 1 et 100");
    }

    /** Rang du premier point de la page dans l'historique. */
    public long rang() {
        return (long) page * taille;
    }
}
