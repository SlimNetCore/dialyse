package com.hemodialyse.backend.contexts.documents.application;

/** Le modèle (du centre de la session), la version ou le gabarit demandé n'existe pas : 404 sans corps, comme avant. */
public class GabaritIntrouvable extends RuntimeException {

    public GabaritIntrouvable() {
        super("Gabarit introuvable");
    }
}
