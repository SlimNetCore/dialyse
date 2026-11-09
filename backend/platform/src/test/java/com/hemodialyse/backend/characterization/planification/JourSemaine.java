package com.hemodialyse.backend.characterization.planification;

/**
 * Jour de dialyse de la semaine des HARNAIS de caractérisation (même ordre que les colonnes {@code jour_dimanche}… {@code jour_samedi}
 * de la fiche patient : dimanche → samedi). Classe de TEST : remplace l'énumération legacy {@code domain.planning.model.JourSemaine}
 * de {@code bc-seance}, retirée par SE7f (ADR 0100), que ces harnais importaient ; aucun code de production ne la connaît.
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

    /** Jour de la semaine correspondant à un {@link java.time.DayOfWeek} (la semaine commence le dimanche). */
    public static JourSemaine de(java.time.DayOfWeek jour) {
        return values()[jour.getValue() % NB_JOURS];
    }
}
