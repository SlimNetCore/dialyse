package com.hemodialyse.backend.domain.medical.ordonnance.specification;

import com.hemodialyse.backend.domain.medical.ordonnance.valueobject.StatutOrdonnance;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Specification (AGENTS.md §14) : la machine à états d'une ordonnance.
 * <p>
 * {@code BROUILLON → SIGNEE → IMPRIMEE}, avec {@code ANNULEE} accessible depuis tout statut non
 * terminal. {@code ANNULEE} est terminal ; {@code IMPRIMEE} ne peut plus être signée à nouveau
 * (la réimpression n'est pas une transition de statut, elle ne modifie pas {@code statut}).
 */
public final class TransitionStatutOrdonnanceSpecification {

    private static final Map<StatutOrdonnance, Set<StatutOrdonnance>> TRANSITIONS_AUTORISEES =
            new EnumMap<>(StatutOrdonnance.class);

    static {
        TRANSITIONS_AUTORISEES.put(StatutOrdonnance.BROUILLON,
                EnumSet.of(StatutOrdonnance.SIGNEE, StatutOrdonnance.ANNULEE));
        TRANSITIONS_AUTORISEES.put(StatutOrdonnance.SIGNEE,
                EnumSet.of(StatutOrdonnance.IMPRIMEE, StatutOrdonnance.ANNULEE));
        TRANSITIONS_AUTORISEES.put(StatutOrdonnance.IMPRIMEE,
                EnumSet.of(StatutOrdonnance.ANNULEE));
        TRANSITIONS_AUTORISEES.put(StatutOrdonnance.ANNULEE, EnumSet.noneOf(StatutOrdonnance.class));
    }

    private TransitionStatutOrdonnanceSpecification() {
    }

    public static boolean estAutorisee(StatutOrdonnance depuis, StatutOrdonnance vers) {
        return TRANSITIONS_AUTORISEES.getOrDefault(depuis, EnumSet.noneOf(StatutOrdonnance.class)).contains(vers);
    }
}
