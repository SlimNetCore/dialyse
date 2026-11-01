package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Specification (AGENTS.md \u00a714) : la machine \u00e0 \u00e9tats d'une demande d'examen.
 * <p>
 * {@code DEMANDE \u2192 PRELEVE \u2192 RESULTAT_DISPONIBLE \u2192 VALIDE}, avec {@code ANNULE} accessible
 * uniquement avant que le r\u00e9sultat ne soit disponible — annuler apr\u00e8s coup masquerait un
 * r\u00e9sultat d\u00e9j\u00e0 produit plut\u00f4t que de refl\u00e9ter une d\u00e9cision clinique.
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
