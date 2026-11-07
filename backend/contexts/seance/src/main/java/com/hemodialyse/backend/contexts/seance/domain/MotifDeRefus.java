package com.hemodialyse.backend.contexts.seance.domain;

/**
 * Cause métier d'un refus de séance. {@link #code()} est le code d'erreur de l'API observé par le filet de caractérisation (422) ;
 * les trois causes de facturation partagent le code générique {@code BUSINESS_RULE_VIOLATION} (KB-SEA-10, reproduit).
 */
public enum MotifDeRefus {
    PRISE_EN_CHARGE_ABSENTE("BUSINESS_RULE_VIOLATION"),
    PRISE_EN_CHARGE_NON_VALIDEE("BUSINESS_RULE_VIOLATION"),
    ATTESTATION_DE_DROITS_ABSENTE("BUSINESS_RULE_VIOLATION"),
    CENTRE_FERME("SEANCE_CENTRE_FERME"),
    JOUR_NON_DIALYSE("SEANCE_HORS_PLANNING_JOUR"),
    PATIENT_NON_ATTENDU("SEANCE_HORS_PLANNING_PATIENT"),
    MOTIF_DE_DEROGATION_REQUIS("SEANCE_DEROGATION_MOTIF_REQUIS"),
    PRECISION_DE_DEROGATION_REQUISE("SEANCE_DEROGATION_PRECISION_REQUISE"),
    SEANCE_DU_JOUR_ABSENTE("BUSINESS_RULE_VIOLATION");

    private final String code;

    MotifDeRefus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
