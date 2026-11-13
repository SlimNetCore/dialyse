package com.hemodialyse.backend.contexts.organisation.domain;

/** Le code saisi désigne déjà un créneau proposé du centre. */
public class CreneauDejaExistant extends RuntimeException {
    public CreneauDejaExistant(String message) {
        super(message);
    }
}
