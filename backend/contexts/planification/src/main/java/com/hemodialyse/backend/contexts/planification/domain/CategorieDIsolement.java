package com.hemodialyse.backend.contexts.planification.domain;

/**
 * Catégorie d'isolement d'un patient : à risque infectieux si la dernière sérologie d'au moins un marqueur est positive
 * (RG-PLN-012). Un patient à risque n'est placé qu'en salle d'isolement ; un patient sans risque n'y est jamais placé
 * (décision du 27/10, KB-PLN-55) ; un générateur ne mélange jamais les deux catégories (RG-PLN-024).
 */
public enum CategorieDIsolement {
    A_RISQUE,
    SANS_RISQUE;

    public static CategorieDIsolement deduite(boolean serologiePositive) {
        return serologiePositive ? A_RISQUE : SANS_RISQUE;
    }

    public boolean aRisque() {
        return this == A_RISQUE;
    }
}
