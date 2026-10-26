package com.hemodialyse.backend.contexts.planification.domain;

/**
 * Réglages de planification d'un centre (RG-PLN-098) : durée d'une vacation (1 à 12 h, 5 par défaut), heures d'un temps plein
 * (10 à 60 h, 40), repos hebdomadaire minimal (0 à 6 jours, 1) et replanification nocturne (non par défaut).
 */
public record ReglagesDePlanification(boolean replanificationNocturne, int heuresParVacation, int heuresHebdoTempsPlein,
                                      int reposHebdoMin) {

    public static final int HEURES_VACATION_MAX = 12;
    public static final int HEURES_VACATION_PAR_DEFAUT = 5;
    public static final int HEURES_HEBDO_MIN = 10;
    public static final int HEURES_HEBDO_MAX = 60;
    public static final int HEURES_HEBDO_PAR_DEFAUT = 40;
    public static final int REPOS_MAX = 6;
    public static final int REPOS_PAR_DEFAUT = 1;

    public ReglagesDePlanification {
        if (heuresParVacation < 1 || heuresParVacation > HEURES_VACATION_MAX) {
            throw new SaisieInvalide("La durée d'une vacation doit être comprise entre 1 et " + HEURES_VACATION_MAX + " heures");
        }
        if (heuresHebdoTempsPlein < HEURES_HEBDO_MIN || heuresHebdoTempsPlein > HEURES_HEBDO_MAX) {
            throw new SaisieInvalide("Les heures hebdomadaires d'un temps plein doivent être comprises entre "
                    + HEURES_HEBDO_MIN + " et " + HEURES_HEBDO_MAX);
        }
        if (reposHebdoMin < 0 || reposHebdoMin > REPOS_MAX) {
            throw new SaisieInvalide("Le repos hebdomadaire doit être compris entre 0 et " + REPOS_MAX + " jours");
        }
    }

    public static ReglagesDePlanification parDefaut() {
        return new ReglagesDePlanification(false, HEURES_VACATION_PAR_DEFAUT, HEURES_HEBDO_PAR_DEFAUT, REPOS_PAR_DEFAUT);
    }
}
