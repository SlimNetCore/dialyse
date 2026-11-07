package com.hemodialyse.backend.contexts.dossiermedical.domain;

/** Une règle de gestion du dossier médical refuse l'opération : 422 avec un code stable. Aucune donnée de santé dans le message. */
public class RegleDeGestionViolee extends RuntimeException {
    private final String code;

    public RegleDeGestionViolee(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
