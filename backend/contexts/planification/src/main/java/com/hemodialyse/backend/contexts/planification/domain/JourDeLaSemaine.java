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

    public static JourDeLaSemaine de(DayOfWeek jour) {
        return values()[jour.getValue() % values().length];
    }

    public DayOfWeek jourCivil() {
        return DayOfWeek.of(ordinal() == 0 ? 7 : ordinal());
    }
}
