package com.hemodialyse.backend.contexts.organisation.domain;

/** Le nom et le prénom saisis désignent déjà un médecin proposé du centre. */
public class MedecinDejaExistant extends RuntimeException {
    public MedecinDejaExistant(String message) {
        super(message);
    }
}
