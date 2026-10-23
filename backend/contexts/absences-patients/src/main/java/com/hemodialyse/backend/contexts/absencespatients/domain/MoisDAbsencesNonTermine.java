package com.hemodialyse.backend.contexts.absencespatients.domain;

import java.time.YearMonth;

/**
 * Refus 422 {@code MOIS_D_ABSENCES_NON_TERMINE} : le mois en cours et les mois futurs ne se closent pas (au fuseau du centre, sans
 * jours de grâce : le mois précédent se clôt dès le premier jour du mois).
 */
public final class MoisDAbsencesNonTermine extends RegleDAbsenceViolee {

    private final YearMonth mois;

    public MoisDAbsencesNonTermine(YearMonth mois) {
        super("MOIS_D_ABSENCES_NON_TERMINE", "Le mois d'absences " + mois + " n'est pas terminé : seul un mois passé peut être clos");
        this.mois = mois;
    }

    public YearMonth mois() {
        return mois;
    }
}
