package com.hemodialyse.backend.contexts.absencespatients.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Semaine du planning : du dimanche au samedi inclus (la grille du planning est bornée à sept jours, donc non paginée).
 */
public record SemaineDuPlanning(LocalDate dimanche) {

    public SemaineDuPlanning {
        if (dimanche == null || dimanche.getDayOfWeek() != DayOfWeek.SUNDAY) {
            throw new IllegalArgumentException("Une semaine du planning commence un dimanche");
        }
    }

    /**
     * La semaine qui contient ce jour.
     */
    public static SemaineDuPlanning contenant(LocalDate jour) {
        return new SemaineDuPlanning(jour.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)));
    }

    public LocalDate samedi() {
        return dimanche.plusDays(6);
    }
}
