package com.hemodialyse.backend.contexts.planification.domain;

/**
 * Ratio de sécurité tel que la présence le LIT (KB-PLN-47, décision du 27/10/2026) : une valeur de 1 à {@value #MAXIMUM_LU} est
 * utilisée telle quelle ; une valeur absente
 * vaut le défaut en silence ; une valeur présente mais inutilisable (0, négative, au-delà de la borne) est remplacée par le défaut et
 * {@code remplaceParLeDefaut} le signale, pour que l'adapter la journalise. Le calcul de présence ne s'arrête donc jamais sur une
 * donnée héritée illisible.
 */
public record RatioDeLaPresence(int patientsParInfirmier, boolean remplaceParLeDefaut) {

    /** Même borne qu'à l'écriture ({@link RatioDeSecurite#MAXIMUM}) : aucune route n'a pu écrire au-delà. */
    public static final int MAXIMUM_LU = RatioDeSecurite.MAXIMUM;

    public RatioDeLaPresence {
        if (patientsParInfirmier < 1) {
            throw new IllegalArgumentException("Un ratio utilisable est d'au moins 1");
        }
    }

    public static RatioDeLaPresence depuisLaBase(Integer valeur) {
        if (valeur == null) {
            return new RatioDeLaPresence(RatioDeSecurite.PAR_DEFAUT, false);
        }
        if (valeur < 1 || valeur > MAXIMUM_LU) {
            return new RatioDeLaPresence(RatioDeSecurite.PAR_DEFAUT, true);
        }
        return new RatioDeLaPresence(valeur, false);
    }
}
