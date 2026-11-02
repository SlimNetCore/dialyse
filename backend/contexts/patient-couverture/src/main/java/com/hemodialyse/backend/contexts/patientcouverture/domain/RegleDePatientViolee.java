package com.hemodialyse.backend.contexts.patientcouverture.domain;

/**
 * Refus métier de la fiche du patient, avec un code stable (422) : {@code PATIENT_ETAT_INCONNU},
 * {@code PATIENT_DATE_EVENEMENT_REQUISE}, {@code PATIENT_DATE_EVENEMENT_AVANT_ADMISSION},
 * {@code PATIENT_DATE_NAISSANCE_FUTURE}, {@code PATIENT_DATE_NAISSANCE_APRES_ADMISSION},
 * {@code PATIENT_NUMERO_ASSURANCE_DEJA_UTILISE}, {@code PATIENT_ATTESTATION_REQUISE}.
 */
public class RegleDePatientViolee extends RuntimeException {

    private final String code;

    public RegleDePatientViolee(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
