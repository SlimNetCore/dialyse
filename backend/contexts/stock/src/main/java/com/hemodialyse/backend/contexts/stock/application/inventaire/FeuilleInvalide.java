package com.hemodialyse.backend.contexts.stock.application.inventaire;

/**
 * La feuille de comptage importée est inutilisable en l'état (PL7, KB-PL7-4, décision du 2026-11-12) : titre de référence absent,
 * motif d'écart sur un lot sans écart, partie « file » absente. Réponse 400 {@code BAD_REQUEST} avec un code stable.
 */
public class FeuilleInvalide extends RuntimeException {

    private final String code;

    public FeuilleInvalide(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
