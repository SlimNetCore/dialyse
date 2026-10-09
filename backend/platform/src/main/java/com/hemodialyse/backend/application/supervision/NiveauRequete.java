package com.hemodialyse.backend.application.supervision;

/**
 * Gravité d'une requête, pour mettre en évidence celles à améliorer en priorité.
 */
public enum NiveauRequete {
    NORMAL,
    ATTENTION,
    CRITIQUE;

    /**
     * Durée moyenne à partir de laquelle une requête est jugée critique (un écran perceptiblement lent).
     */
    static final double SEUIL_CRITIQUE_MS = 500;
    /**
     * Durée moyenne à partir de laquelle une requête mérite attention.
     */
    static final double SEUIL_ATTENTION_MS = 100;
    /**
     * Part du temps total de la base à partir de laquelle une requête mérite attention, même si elle est rapide.
     */
    static final double SEUIL_PART_ATTENTION_PCT = 20;

    /**
     * @param tempsMoyenMs durée moyenne d'un appel
     * @param partTotalPct part (en %) du temps cumulé de toute la base
     */
    public static NiveauRequete evaluer(double tempsMoyenMs, double partTotalPct) {
        if (tempsMoyenMs >= SEUIL_CRITIQUE_MS) {
            return CRITIQUE;
        }
        if (tempsMoyenMs >= SEUIL_ATTENTION_MS || partTotalPct >= SEUIL_PART_ATTENTION_PCT) {
            return ATTENTION;
        }
        return NORMAL;
    }
}
