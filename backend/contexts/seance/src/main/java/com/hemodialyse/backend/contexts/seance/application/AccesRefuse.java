package com.hemodialyse.backend.contexts.seance.application;

/** L'utilisateur n'a pas le droit d'agir sur ce centre : 403. Le message dit pourquoi, sans aucune donnée de santé. */
public class AccesRefuse extends RuntimeException {
    public AccesRefuse() {
        super("Accès refusé");
    }

    public AccesRefuse(String motif) {
        super(motif);
    }
}
