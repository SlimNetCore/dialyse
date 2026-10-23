package com.hemodialyse.backend.contexts.stock.domain.groupes;

/**
 * Composition de groupe inutilisable (nom, description ou nombre d'articles) : 400 {@code BAD_REQUEST}, comme le legacy.
 */
public class GroupeDArticlesInvalide extends RuntimeException {

    public GroupeDArticlesInvalide(String message) {
        super(message);
    }
}

