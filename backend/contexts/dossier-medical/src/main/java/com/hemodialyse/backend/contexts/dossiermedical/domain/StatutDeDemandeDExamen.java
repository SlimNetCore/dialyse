package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Cycle de vie d'une demande d'examen : {@code DEMANDE -> PRELEVE -> RESULTAT_DISPONIBLE -> VALIDE}, {@code ANNULE} depuis
 * DEMANDE ou PRELEVE seulement (annuler après le résultat masquerait un résultat déjà produit). VALIDE et ANNULE sont terminaux.
 */
public enum StatutDeDemandeDExamen {
    DEMANDE, PRELEVE, RESULTAT_DISPONIBLE, VALIDE, ANNULE;

    public Set<StatutDeDemandeDExamen> suivants() {
        return switch (this) {
            case DEMANDE -> EnumSet.of(PRELEVE, ANNULE);
            case PRELEVE -> EnumSet.of(RESULTAT_DISPONIBLE, ANNULE);
            case RESULTAT_DISPONIBLE -> EnumSet.of(VALIDE);
            case VALIDE, ANNULE -> EnumSet.noneOf(StatutDeDemandeDExamen.class);
        };
    }

    public boolean peutPasserA(StatutDeDemandeDExamen vers) {
        return suivants().contains(vers);
    }
}
