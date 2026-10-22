package com.hemodialyse.backend.contexts.absencespatients.domain;

/**
 * Une règle métier du suivi des absences est violée : 422 et un code stable (les codes du legacy sont conservés :
 * {@code ABSENCE_DATE_FUTURE}, {@code ABSENCE_MOTIF_REQUIS}, {@code ABSENCE_PERIODE_FACTUREE}...).
 */
public class RegleDAbsenceViolee extends RuntimeException {
    private final String code;

    public RegleDAbsenceViolee(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
