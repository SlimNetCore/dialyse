package com.hemodialyse.backend.contexts.planification.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Période d'absence, bornes incluses : début au plus tard égal à la fin, au plus 366 jours (RG-INF-030).
 */
public record PeriodeDAbsence(LocalDate debut, LocalDate fin) {

    public static final int DUREE_MAX_JOURS = 366;

    public PeriodeDAbsence {
        if (debut == null || fin == null) {
            throw new SaisieInvalide("Période requise");
        }
        if (fin.isBefore(debut)) {
            throw new SaisieInvalide("La fin de l'absence précède son début");
        }
        if (ChronoUnit.DAYS.between(debut, fin) + 1 > DUREE_MAX_JOURS) {
            throw new SaisieInvalide("Une absence ne peut pas dépasser " + DUREE_MAX_JOURS + " jours");
        }
    }

    public boolean couvre(LocalDate date) {
        return !date.isBefore(debut) && !date.isAfter(fin);
    }

    /**
     * Partagent-elles au moins un jour ? Deux périodes contiguës (fin la veille du début) ne se chevauchent pas.
     */
    public boolean chevauche(PeriodeDAbsence autre) {
        return !autre.debut.isAfter(fin) && !autre.fin.isBefore(debut);
    }
}
