package com.hemodialyse.backend.contexts.seance.application;

/** Le Stock refuse les consommables de la séance (séance facturée, article inconnu, quantité invalide) : 422 avec le code du Stock. */
public class ConsommationRefusee extends RuntimeException {

    private final String code;

    public ConsommationRefusee(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
