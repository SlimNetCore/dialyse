package com.hemodialyse.backend.contexts.organisation.domain;

/**
 * Refus d'un logo de société : code stable traduit côté interface (422 {@code violations}).
 * Volontairement hors hiérarchie {@link IllegalArgumentException} pour ne pas être
 * confondu avec une requête mal formée (400) par le gestionnaire global d'exceptions.
 */
public class LogoRefuse extends RuntimeException {

    private final String code;
    private final String detail;

    public LogoRefuse(String code, String detail) {
        super(code + (detail == null ? "" : " : " + detail));
        this.code = code;
        this.detail = detail;
    }

    public String code() {
        return code;
    }

    public String detail() {
        return detail;
    }
}
