package com.hemodialyse.backend.domain.medical.examen.specification;

import com.hemodialyse.backend.domain.medical.examen.valueobject.StatutDemandeExamen;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Specification (AGENTS.md §14) : la machine à états d'une demande d'examen.
 * <p>
 * {@code DEMANDE → PRELEVE → RESULTAT_DISPONIBLE → VALIDE}, avec {@code ANNULE} accessible
 * uniquement avant que le résultat ne soit disponible — annuler après coup masquerait un
 * résultat déjà produit plutôt que de refléter une décision clinique.
 */
public final class TransitionStatutDemandeSpecification {

    private static final Map<StatutDemandeExamen, Set<StatutDemandeExamen>> TRANSITIONS_AUTORISEES =
            new EnumMap<>(StatutDemandeExamen.class);

    static {
        TRANSITIONS_AUTORISEES.put(StatutDemandeExamen.DEMANDE,
                EnumSet.of(StatutDemandeExamen.PRELEVE, StatutDemandeExamen.ANNULE));
        TRANSITIONS_AUTORISEES.put(StatutDemandeExamen.PRELEVE,
                EnumSet.of(StatutDemandeExamen.RESULTAT_DISPONIBLE, StatutDemandeExamen.ANNULE));
        TRANSITIONS_AUTORISEES.put(StatutDemandeExamen.RESULTAT_DISPONIBLE,
                EnumSet.of(StatutDemandeExamen.VALIDE));
        TRANSITIONS_AUTORISEES.put(StatutDemandeExamen.VALIDE, EnumSet.noneOf(StatutDemandeExamen.class));
        TRANSITIONS_AUTORISEES.put(StatutDemandeExamen.ANNULE, EnumSet.noneOf(StatutDemandeExamen.class));
    }

    private TransitionStatutDemandeSpecification() {
    }

    public static boolean estAutorisee(StatutDemandeExamen depuis, StatutDemandeExamen vers) {
        return TRANSITIONS_AUTORISEES.getOrDefault(depuis, EnumSet.noneOf(StatutDemandeExamen.class)).contains(vers);
    }
}
