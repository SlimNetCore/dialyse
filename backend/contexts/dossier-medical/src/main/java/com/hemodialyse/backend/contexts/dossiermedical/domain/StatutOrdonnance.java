package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Cycle de vie d'une ordonnance : {@code BROUILLON -> SIGNEE -> IMPRIMEE}, {@code ANNULEE} depuis tout statut non terminal
 * (annuler une ordonnance signée ou imprimée est une décision clinique, pas une correction en place). {@code ANNULEE} est
 * terminal ; la réimpression n'est pas une transition (reproduit du legacy : une ordonnance déjà imprimée ne s'imprime plus
 * « une seconde fois » par cette route, elle est refusée).
 */
public enum StatutOrdonnance {
    BROUILLON, SIGNEE, IMPRIMEE, ANNULEE;

    public Set<StatutOrdonnance> suivants() {
        return switch (this) {
            case BROUILLON -> EnumSet.of(SIGNEE, ANNULEE);
            case SIGNEE -> EnumSet.of(IMPRIMEE, ANNULEE);
            case IMPRIMEE -> EnumSet.of(ANNULEE);
            case ANNULEE -> EnumSet.noneOf(StatutOrdonnance.class);
        };
    }

    public boolean peutPasserA(StatutOrdonnance vers) {
        return suivants().contains(vers);
    }
}
