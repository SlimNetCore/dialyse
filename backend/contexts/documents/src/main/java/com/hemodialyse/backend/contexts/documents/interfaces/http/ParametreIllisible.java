package com.hemodialyse.backend.contexts.documents.interfaces.http;

/** Un paramètre de la requête n'a pas la forme attendue : 400 {@code BAD_REQUEST} qui nomme le paramètre. */
class ParametreIllisible extends RuntimeException {

    private final String nom;

    ParametreIllisible(String nom) {
        super("Paramètre illisible : " + nom);
        this.nom = nom;
    }

    String nom() {
        return nom;
    }
}
