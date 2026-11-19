package com.hemodialyse.backend.contexts.facturation.application.consultation;

/** Critères de la liste des factures refusés (400 avec un code stable : {@code PERIODE_INVALIDE}, {@code MONTANT_INVALIDE}). */
public final class CriteresDeFacturesInvalides extends RuntimeException {

    public static final String PERIODE_INVALIDE = "PERIODE_INVALIDE";
    public static final String MONTANT_INVALIDE = "MONTANT_INVALIDE";

    private final String code;

    public CriteresDeFacturesInvalides(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
