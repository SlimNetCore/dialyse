package com.hemodialyse.backend.domain.planning.optimisation.model;

/**
 * Réglages de l'optimisation d'un centre : contraintes de personnel (durée d'une vacation, heures hebdomadaires d'un
 * temps plein, jours de repos minimaux par semaine) et replanification automatique nocturne.
 */
public record ReglagesOptimisation(boolean replanificationAuto, int heuresParVacation, int heuresHebdoTempsPlein,
                                   int reposHebdoMin) {

    public static final int HEURES_VACATION_MAX = 12;
    public static final int HEURES_VACATION_PAR_DEFAUT = 5;
    public static final int HEURES_HEBDO_MIN = 10;
    public static final int HEURES_HEBDO_MAX = 60;
    public static final int HEURES_HEBDO_PAR_DEFAUT = 40;
    public static final int REPOS_MAX = 6;
    public static final int REPOS_PAR_DEFAUT = 1;

    public ReglagesOptimisation {
        if (heuresParVacation < 1 || heuresParVacation > HEURES_VACATION_MAX) {
            throw new IllegalArgumentException("La durée d'une vacation doit être comprise entre 1 et "
                    + HEURES_VACATION_MAX + " heures");
        }
        if (heuresHebdoTempsPlein < HEURES_HEBDO_MIN || heuresHebdoTempsPlein > HEURES_HEBDO_MAX) {
            throw new IllegalArgumentException("Les heures hebdomadaires d'un temps plein doivent être comprises entre "
                    + HEURES_HEBDO_MIN + " et " + HEURES_HEBDO_MAX);
        }
        if (reposHebdoMin < 0 || reposHebdoMin > REPOS_MAX) {
            throw new IllegalArgumentException("Le repos hebdomadaire doit être compris entre 0 et " + REPOS_MAX + " jours");
        }
    }

    public static ReglagesOptimisation parDefaut() {
        return new ReglagesOptimisation(false, HEURES_VACATION_PAR_DEFAUT, HEURES_HEBDO_PAR_DEFAUT, REPOS_PAR_DEFAUT);
    }
}
