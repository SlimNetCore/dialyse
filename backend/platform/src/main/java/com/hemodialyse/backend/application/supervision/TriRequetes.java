package com.hemodialyse.backend.application.supervision;

import java.util.Locale;

/**
 * Critère de classement des requêtes, de la plus coûteuse à la moins coûteuse.
 */
public enum TriRequetes {
    /**
     * Temps cumulé : les requêtes qui pèsent le plus sur la base (rapides mais très fréquentes, ou lentes).
     */
    TEMPS_TOTAL,
    /**
     * Durée moyenne d'un appel : les requêtes lentes à chaque exécution (les écrans qui « rament »).
     */
    TEMPS_MOYEN,
    /**
     * Nombre d'exécutions : les requêtes les plus sollicitées.
     */
    APPELS;

    /**
     * Valeur inconnue ou absente : temps total, le classement le plus utile pour décider quoi optimiser.
     */
    public static TriRequetes depuis(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return TEMPS_TOTAL;
        }
        try {
            return valueOf(valeur.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return TEMPS_TOTAL;
        }
    }
}
