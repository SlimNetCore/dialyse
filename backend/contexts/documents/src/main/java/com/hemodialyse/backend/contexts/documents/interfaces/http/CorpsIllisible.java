package com.hemodialyse.backend.contexts.documents.interfaces.http;

/** Le corps JSON de la requête est absent, mal formé ou d'une forme inattendue : 400 {@code BAD_REQUEST}. */
class CorpsIllisible extends RuntimeException {

    CorpsIllisible(String detail) {
        super(detail);
    }
}
