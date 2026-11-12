package com.hemodialyse.backend.contexts.gmao.interfaces.http;

/** Identifiant de la route mal formé (adapter entrant) : 400. */
class IdentifiantIllisible extends RuntimeException {

    IdentifiantIllisible() {
        super("Identifiant illisible");
    }
}
