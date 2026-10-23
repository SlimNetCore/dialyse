package com.hemodialyse.backend.contexts.absencespatients.domain;

import java.time.YearMonth;

/**
 * Refus 409 {@code MOIS_D_ABSENCES_NON_CLOS} : on ne rouvre pas un mois qui n'est pas clos.
 */
public final class MoisDAbsencesNonClos extends RuntimeException {
    public MoisDAbsencesNonClos(YearMonth mois) {
        super("Le mois d'absences " + mois + " n'est pas clos");
    }
}
