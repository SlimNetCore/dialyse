package com.hemodialyse.backend.contexts.stock.domain.sortie;

/**
 * Demande de sortie inutilisable (ligne incomplète, article, lot ou bon introuvable, lot d'un autre article) : 400
 * {@code BAD_REQUEST}, messages du legacy conservés.
 */
public class SortieInvalide extends RuntimeException {

    public SortieInvalide(String message) {
        super(message);
    }
}

