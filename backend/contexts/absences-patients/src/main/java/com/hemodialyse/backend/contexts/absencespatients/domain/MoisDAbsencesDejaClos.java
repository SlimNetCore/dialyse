package com.hemodialyse.backend.contexts.absencespatients.domain;

import java.time.YearMonth;

/**
 * Refus 409 {@code MOIS_D_ABSENCES_DEJA_CLOS} : on ne clôt pas deux fois le même mois.
 */
public final class MoisDAbsencesDejaClos extends RuntimeException {
    public MoisDAbsencesDejaClos(YearMonth mois) {
        super("Le mois d'absences " + mois + " est déjà clos");
    }
}
