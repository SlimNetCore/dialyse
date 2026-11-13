package com.hemodialyse.backend.contexts.documents.domain;

import java.util.List;

/** Une page de l'historique : la taille est bornée à 1..100 et la page à 0 ou plus (les valeurs hors bornes sont ramenées, jamais refusées). */
public record Pagination(int page, int taille) {

    public static final int TAILLE_MAXIMALE = 100;

    public static Pagination de(int page, int taille) {
        return new Pagination(Math.max(page, 0), Math.min(Math.max(taille, 1), TAILLE_MAXIMALE));
    }

    /** La tranche de cette page dans une liste complète (vide au-delà de la fin). */
    public <T> List<T> tranche(List<T> tous) {
        long debut = (long) page * taille;
        if (debut >= tous.size()) {
            return List.of();
        }
        return List.copyOf(tous.subList((int) debut, (int) Math.min(debut + taille, tous.size())));
    }
}
