package com.hemodialyse.backend.contexts.documents.application;

/** Le moteur n'a pas pu rendre le document (gabarit introuvable, erreur du moteur) : 500, texte brut. {@code cause} peut être une erreur (Error). */
public class RenduEchoue extends RuntimeException {

    public RenduEchoue(Throwable cause) {
        super(cause.getMessage(), cause);
    }

    /** Vrai si la cause est une erreur grave (Error) et non une exception. */
    public boolean estCritique() {
        return !(getCause() instanceof Exception);
    }
}
