package com.hemodialyse.backend.domain.medical.greffe.specification;

import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutBilanGreffe;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Specification (AGENTS.md §14) : la machine à états du statut de bilan pré-greffe.
 * <p>
 * L'inscription sur liste d'attente ({@code INSCRIT_LISTE_ATTENTE}) n'est atteignable que
 * depuis {@code ELIGIBLE} — un patient ne peut pas être inscrit sans être d'abord passé par une
 * évaluation d'éligibilité formelle. {@code CONTRE_INDICATION_DEFINITIVE} et
 * {@code GREFFE_REALISEE} sont terminaux.
 */
public final class TransitionStatutBilanGreffeSpecification {

    private static final Map<StatutBilanGreffe, Set<StatutBilanGreffe>> TRANSITIONS_AUTORISEES =
            new EnumMap<>(StatutBilanGreffe.class);

    static {
        TRANSITIONS_AUTORISEES.put(StatutBilanGreffe.NON_DEBUTE,
                EnumSet.of(StatutBilanGreffe.BILAN_EN_COURS, StatutBilanGreffe.CONTRE_INDICATION_DEFINITIVE));
        TRANSITIONS_AUTORISEES.put(StatutBilanGreffe.BILAN_EN_COURS,
                EnumSet.of(StatutBilanGreffe.ELIGIBLE, StatutBilanGreffe.CONTRE_INDICATION_TEMPORAIRE,
                        StatutBilanGreffe.CONTRE_INDICATION_DEFINITIVE));
        TRANSITIONS_AUTORISEES.put(StatutBilanGreffe.ELIGIBLE,
                EnumSet.of(StatutBilanGreffe.INSCRIT_LISTE_ATTENTE, StatutBilanGreffe.CONTRE_INDICATION_TEMPORAIRE,
                        StatutBilanGreffe.CONTRE_INDICATION_DEFINITIVE));
        TRANSITIONS_AUTORISEES.put(StatutBilanGreffe.INSCRIT_LISTE_ATTENTE,
                EnumSet.of(StatutBilanGreffe.GREFFE_REALISEE, StatutBilanGreffe.CONTRE_INDICATION_TEMPORAIRE,
                        StatutBilanGreffe.CONTRE_INDICATION_DEFINITIVE));
        TRANSITIONS_AUTORISEES.put(StatutBilanGreffe.CONTRE_INDICATION_TEMPORAIRE,
                EnumSet.of(StatutBilanGreffe.BILAN_EN_COURS, StatutBilanGreffe.CONTRE_INDICATION_DEFINITIVE));
        TRANSITIONS_AUTORISEES.put(StatutBilanGreffe.CONTRE_INDICATION_DEFINITIVE,
                EnumSet.noneOf(StatutBilanGreffe.class));
        TRANSITIONS_AUTORISEES.put(StatutBilanGreffe.GREFFE_REALISEE,
                EnumSet.noneOf(StatutBilanGreffe.class));
    }

    private TransitionStatutBilanGreffeSpecification() {
    }

    public static boolean estAutorisee(StatutBilanGreffe depuis, StatutBilanGreffe vers) {
        if (depuis == vers) {
            return true;
        }
        return TRANSITIONS_AUTORISEES.getOrDefault(depuis, EnumSet.noneOf(StatutBilanGreffe.class)).contains(vers);
    }
}
