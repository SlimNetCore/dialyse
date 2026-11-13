package com.hemodialyse.backend.contexts.documents.application;

/** Le code du modèle est déjà pris par un autre modèle du centre, ou l'identifiant est déjà attribué : 409 {@code CONFLICT}. */
public class ModeleEnDoublon extends RuntimeException {

    public ModeleEnDoublon(String message, Throwable cause) {
        super(message, cause);
    }
}
