package com.hemodialyse.backend.contexts.absencespatients.interfaces.http;

/**
 * Paramètre de requête illisible (mois ou année) : 400 avec un code stable.
 */
final class RequeteInvalide extends RuntimeException {

    private final String code;

    RequeteInvalide(String code, String message) {
        super(message);
        this.code = code;
    }

    String code() {
        return code;
    }
}
