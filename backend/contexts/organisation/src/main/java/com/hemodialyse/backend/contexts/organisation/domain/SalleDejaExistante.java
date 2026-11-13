package com.hemodialyse.backend.contexts.organisation.domain;

/** Une autre salle proposée du centre porte déjà ce code. */
public class SalleDejaExistante extends RuntimeException {
    public SalleDejaExistante(String message) {
        super(message);
    }
}
