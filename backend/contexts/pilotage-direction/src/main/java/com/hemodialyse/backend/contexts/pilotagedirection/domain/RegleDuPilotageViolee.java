package com.hemodialyse.backend.contexts.pilotagedirection.domain;

/** Une règle du Pilotage direction refuse la demande : 422 avec un code stable ({@code PERIODE_INVALIDE}, {@code PERIODE_TROP_LONGUE}). */
public class RegleDuPilotageViolee extends RuntimeException {
    private final String code;

    public RegleDuPilotageViolee(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
