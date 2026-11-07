package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Aucune analyse avec cet identifiant pour ce patient dans ce centre : 404 {@code ANALYSE_INTROUVABLE} (écart décidé KB-MED-74 : le
 * legacy créait le bilan ou écrasait celui d'un autre patient ou d'un autre centre).
 */
public class AnalyseIntrouvable extends RuntimeException {
    public AnalyseIntrouvable() {
        super("Analyse introuvable");
    }
}
