package com.hemodialyse.backend.contexts.planification.domain;

/**
 * Ratio de sécurité d'un centre : nombre maximal de patients par infirmier, de 1 à {@value #MAXIMUM} (RG-PLN-003). Il fonde
 * l'effectif requis d'une case.
 */
public record RatioDeSecurite(int patientsParInfirmier) {

    public static final int PAR_DEFAUT = 4;
    public static final int MAXIMUM = 20;

    public RatioDeSecurite {
        if (patientsParInfirmier < 1 || patientsParInfirmier > MAXIMUM) {
            throw new SaisieInvalide("Le ratio patients par infirmier doit être compris entre 1 et " + MAXIMUM);
        }
    }

    public static RatioDeSecurite parDefaut() {
        return new RatioDeSecurite(PAR_DEFAUT);
    }
}
