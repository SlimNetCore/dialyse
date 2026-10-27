package com.hemodialyse.backend.contexts.planification.domain;

import java.time.DayOfWeek;

/**
 * Jour de la semaine du planning : la semaine va du dimanche au samedi (même ordre que les jours de la fiche du patient).
 * Les noms sont ceux du JSON historique.
 */
public enum JourDeLaSemaine {
    DIMANCHE,
    LUNDI,
    MARDI,
    MERCREDI,
    JEUDI,
    VENDREDI,
    SAMEDI;

    public static final int NOMBRE_DE_JOURS = 7;

    /** Jours séparant ce jour du suivant, la semaine étant circulaire (samedi vers dimanche = 1). */
    public int ecartAvec(JourDeLaSemaine suivant) {
        int ecart = suivant.ordinal() - this.ordinal();
        return ecart > 0 ? ecart : ecart + NOMBRE_DE_JOURS;
    }

    public static JourDeLaSemaine de(DayOfWeek jour) {
        return values()[jour.getValue() % values().length];
    }

    public DayOfWeek jourCivil() {
        return DayOfWeek.of(ordinal() == 0 ? 7 : ordinal());
    }
}
