package com.hemodialyse.backend.contexts.planification.domain;

/**
 * Pagination demandée sur une liste. Une page ou une taille négative est refusée ({@link PaginationInvalide}, KB-PLN-08
 * corrigé) ; une taille supérieure à {@value #TAILLE_MAXIMALE} est plafonnée à cette valeur (la taille effective est renvoyée
 * dans la page). Une taille de 0 est admise : aucun élément, mais le total est renseigné.
 */
public record Pagination(int page, int taille) {

    public static final int TAILLE_MAXIMALE = 100;
    public static final int TAILLE_PAR_DEFAUT = 20;

    public Pagination {
        if (page < 0) throw new PaginationInvalide("La page ne peut pas être négative");
        if (taille < 0) throw new PaginationInvalide("La taille de la page ne peut pas être négative");
        taille = Math.min(taille, TAILLE_MAXIMALE);
    }

    /** Nombre d'éléments à sauter avant cette page. */
    public long decalage() {
        return (long) page * taille;
    }
}
