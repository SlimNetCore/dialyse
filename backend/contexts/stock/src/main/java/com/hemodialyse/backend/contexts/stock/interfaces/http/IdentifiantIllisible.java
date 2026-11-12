package com.hemodialyse.backend.contexts.stock.interfaces.http;

/** Paramètre de l'impression du PV ou de la feuille de comptage mal formé (PL7, KB-PL7-1) : 400. */
class IdentifiantIllisible extends RuntimeException {

    IdentifiantIllisible() {
        super("Paramètre illisible");
    }
}
