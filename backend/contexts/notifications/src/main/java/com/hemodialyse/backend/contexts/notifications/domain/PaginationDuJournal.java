package com.hemodialyse.backend.contexts.notifications.domain;

/** Page du journal : la page part de zéro, la taille est bornée de 1 à 100 (50 par défaut). */
public record PaginationDuJournal(int page, int taille) {

    public static final int TAILLE_PAR_DEFAUT = 50;
    public static final int TAILLE_MAX = 100;

    public static PaginationDuJournal de(int page, int taille) {
        return new PaginationDuJournal(Math.max(page, 0), Math.min(Math.max(taille, 1), TAILLE_MAX));
    }
}
