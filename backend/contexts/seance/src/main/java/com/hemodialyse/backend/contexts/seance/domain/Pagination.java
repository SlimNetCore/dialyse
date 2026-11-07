package com.hemodialyse.backend.contexts.seance.domain;

/** Page demandée de l'historique : page négative ramenée à 0, taille bornée entre 1 et 500 (comportement du legacy, sans refus). */
public record Pagination(int page, int taille) {
    public static final int TAILLE_MAXIMALE = 500;

    public Pagination {
        page = Math.max(0, page);
        taille = Math.max(1, Math.min(taille, TAILLE_MAXIMALE));
    }

    public int decalage() {
        return page * taille;
    }
}
