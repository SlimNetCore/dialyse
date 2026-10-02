package com.hemodialyse.backend.domain.planning.model;

/**
 * Jour de dialyse de la semaine (même ordre que les jours enregistrés sur la fiche patient : dimanche → samedi).
 */
public enum JourSemaine {
    DIMANCHE,
    LUNDI,
    MARDI,
    MERCREDI,
    JEUDI,
    VENDREDI,
    SAMEDI;

    public static final int NB_JOURS = 7;

    /**
     * Jour de la semaine correspondant à un {@link java.time.DayOfWeek} (la semaine commence le dimanche).
     */
    public static JourSemaine de(java.time.DayOfWeek jour) {
        return values()[jour.getValue() % NB_JOURS];
    }

    /**
     * Jours séparant ce jour du suivant, la semaine étant circulaire (samedi → dimanche = 1).
     */
    public int ecartAvec(JourSemaine suivant) {
        int ecart = suivant.ordinal() - this.ordinal();
        return ecart > 0 ? ecart : ecart + NB_JOURS;
    }
}
