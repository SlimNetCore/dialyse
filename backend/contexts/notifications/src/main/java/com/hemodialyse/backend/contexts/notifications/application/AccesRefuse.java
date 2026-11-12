package com.hemodialyse.backend.contexts.notifications.application;

/** La session ne donne pas accès au centre demandé (403 {@code ACCESS_DENIED}). */
public class AccesRefuse extends RuntimeException {

    public AccesRefuse(String message) {
        super(message);
    }
}
