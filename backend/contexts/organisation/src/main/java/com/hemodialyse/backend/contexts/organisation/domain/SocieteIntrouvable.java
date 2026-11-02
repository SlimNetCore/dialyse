package com.hemodialyse.backend.contexts.organisation.domain;

/** La société demandée n'existe pas. */
public class SocieteIntrouvable extends IllegalArgumentException {
    public SocieteIntrouvable() {
        super("SOCIETE_INTROUVABLE");
    }
}
