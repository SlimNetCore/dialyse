package com.hemodialyse.backend.contexts.organisation.domain;

/** Le centre demandé n'existe pas (ou n'appartient pas à la société indiquée). */
public class CentreIntrouvable extends IllegalArgumentException {
    public CentreIntrouvable() {
        super("CENTRE_INTROUVABLE");
    }
}
