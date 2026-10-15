package com.hemodialyse.backend.contexts.comptabilite.domain;

/**
 * Une règle du référentiel comptable refuse la demande (422 observé) ; {@link #code()} est le code d'erreur que le
 * frontend connaît déjà ({@code COMPTE_UTILISE}, {@code JOURNAL_INTROUVABLE}...).
 */
public abstract class RegleMetierViolee extends RuntimeException {

    private final String code;

    protected RegleMetierViolee(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
