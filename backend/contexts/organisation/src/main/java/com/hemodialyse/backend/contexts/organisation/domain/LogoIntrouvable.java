package com.hemodialyse.backend.contexts.organisation.domain;

/** La société n'a pas de logo. */
public class LogoIntrouvable extends IllegalArgumentException {
    public LogoIntrouvable() {
        super("LOGO_INTROUVABLE");
    }
}
