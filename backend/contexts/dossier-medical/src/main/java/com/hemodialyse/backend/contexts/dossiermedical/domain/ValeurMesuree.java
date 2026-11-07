package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Valeur numérique d'une observation, toujours accompagnée de son unité (une valeur sans unité n'est pas exploitable cliniquement).
 * Aucune plausibilité (négative, nulle, démesurée : acceptées, KB-MED-79 reproduit, à confirmer avec les métiers).
 * <p>
 * Bornes de colonne (400 au lieu du 500 du legacy, KB-MED-72) : unité 20 caractères, valeur {@code numeric(14,4)} (10 chiffres entiers
 * après arrondi à 4 décimales).
 */
public record ValeurMesuree(BigDecimal valeur, String unite) {
    static final int UNITE_MAXIMALE = 20;
    private static final int PRECISION_MAXIMALE = 14;
    private static final int DECIMALES = 4;

    public ValeurMesuree {
        if (valeur == null) throw new RegleDeGestionViolee("VALEUR_MESUREE_REQUISE", "La valeur mesurée est obligatoire");
        if (unite == null || unite.isBlank()) {
            throw new RegleDeGestionViolee("VALEUR_MESUREE_UNITE_REQUISE", "L'unité est obligatoire pour une valeur numérique");
        }
    }

    /** Refuse (400) une unité ou une valeur qui ne tient pas dans sa colonne. */
    void verifierLesBornes() {
        if (unite.length() > UNITE_MAXIMALE) throw new SaisieInvalide("L'unité est limitée à 20 caractères");
        if (valeur.precision() - valeur.scale() > PRECISION_MAXIMALE - DECIMALES + 1
                || valeur.setScale(DECIMALES, RoundingMode.HALF_UP).precision() > PRECISION_MAXIMALE) {
            throw new SaisieInvalide("La valeur est limitée à 10 chiffres entiers et 4 décimales");
        }
    }
}
