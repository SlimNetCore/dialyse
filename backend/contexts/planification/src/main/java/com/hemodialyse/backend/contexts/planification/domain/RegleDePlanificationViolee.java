package com.hemodialyse.backend.contexts.planification.domain;

/**
 * Refus métier du paramétrage, avec un code stable (422) : {@code PREFERENCE_CRENEAU_INCONNU},
 * {@code PREFERENCE_PATIENT_INTROUVABLE}, {@code PREFERENCE_PATIENT_SORTI}, {@code PROFIL_INFIRMIER_INTROUVABLE},
 * {@code PROFIL_INFIRMIER_INACTIF}.
 */
public class RegleDePlanificationViolee extends RuntimeException {

    private final String code;

    public RegleDePlanificationViolee(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
