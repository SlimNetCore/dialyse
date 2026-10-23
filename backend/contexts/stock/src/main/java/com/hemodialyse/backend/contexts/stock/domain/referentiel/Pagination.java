package com.hemodialyse.backend.contexts.stock.domain.referentiel;

/**
 * Page demandée d'une liste du référentiel (RG-STK-007) : page négative ramenée à 0, taille ramenée entre 1 et 200 (comme le
 * legacy).
 */
public record Pagination(int page, int taille) {

    public static final int TAILLE_MAX = 200;

    public static Pagination demandee(int page, int taille) {
        return new Pagination(Math.max(page, 0), Math.min(Math.max(taille, 1), TAILLE_MAX));
    }

    public long decalage() {
        return (long) page * taille;
    }
}

