package com.hemodialyse.backend.contexts.stock.interfaces.http;

/**
 * Valeur de requête inutilisable (énumération inconnue, paramètre obligatoire absent) : 400 avec un code stable et un message
 * métier (KB-STK-06, 08 corrigés).
 */
class ValeurInvalide extends RuntimeException {

    private final String code;

    ValeurInvalide(String code, String message) {
        super(message);
        this.code = code;
    }

    String code() {
        return code;
    }
}

