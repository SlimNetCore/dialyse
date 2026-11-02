package com.hemodialyse.backend.contexts.patientcouverture.application;

/** L'utilisateur n'a pas le droit d'agir sur ce centre : 403. */
public class AccesRefuse extends RuntimeException {
    public AccesRefuse() {
        super("Accès refusé");
    }
}
