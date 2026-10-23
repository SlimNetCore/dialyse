package com.hemodialyse.backend.contexts.absencespatients.domain;

import java.time.YearMonth;

/**
 * Le mois d'absences est clos : 422 {@code MOIS_D_ABSENCES_CLOS}. Refuse la déclaration, la qualification, le rattrapage (mois de
 * l'absence et mois de la date de rattrapage), l'annulation, l'exclusion d'une séance de la Facturation qui crée une absence, et la
 * réinclusion qui retire celle d'une exclusion : rouvrir d'abord le mois.
 */
public final class MoisDAbsencesClos extends RegleDAbsenceViolee {

    private final YearMonth mois;

    public MoisDAbsencesClos(YearMonth mois) {
        super("MOIS_D_ABSENCES_CLOS", "Le mois d'absences " + mois + " est clos : aucune absence de ce mois ne peut plus être écrite, "
                + "rouvrez d'abord le mois");
        this.mois = mois;
    }

    public YearMonth mois() {
        return mois;
    }
}
