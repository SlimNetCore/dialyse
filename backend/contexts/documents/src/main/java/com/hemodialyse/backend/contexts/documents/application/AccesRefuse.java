package com.hemodialyse.backend.contexts.documents.application;

/** Le centre demandé n'est pas celui de la session (ou la session est invalide) : 403 {@code ACCESS_DENIED}. */
public class AccesRefuse extends RuntimeException {

    public AccesRefuse(String message) {
        super(message);
    }
}
