package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Statut du bilan pré-greffe et machine à états (REPRODUITE du legacy, KB-MED-92, à confirmer avec les métiers) : l'inscription sur
 * liste d'attente n'est atteignable que depuis {@code ELIGIBLE} ; {@code CONTRE_INDICATION_DEFINITIVE} et {@code GREFFE_REALISEE} sont
 * terminaux ; rester dans le même statut est une transition permise.
 */
public enum StatutDuBilanPreGreffe {
    NON_DEBUTE,
    BILAN_EN_COURS,
    ELIGIBLE,
    INSCRIT_LISTE_ATTENTE,
    CONTRE_INDICATION_TEMPORAIRE,
    CONTRE_INDICATION_DEFINITIVE,
    GREFFE_REALISEE;

    private Set<StatutDuBilanPreGreffe> suivants() {
        return switch (this) {
            case NON_DEBUTE -> EnumSet.of(BILAN_EN_COURS, CONTRE_INDICATION_DEFINITIVE);
            case BILAN_EN_COURS -> EnumSet.of(ELIGIBLE, CONTRE_INDICATION_TEMPORAIRE, CONTRE_INDICATION_DEFINITIVE);
            case ELIGIBLE -> EnumSet.of(INSCRIT_LISTE_ATTENTE, CONTRE_INDICATION_TEMPORAIRE, CONTRE_INDICATION_DEFINITIVE);
            case INSCRIT_LISTE_ATTENTE -> EnumSet.of(GREFFE_REALISEE, CONTRE_INDICATION_TEMPORAIRE, CONTRE_INDICATION_DEFINITIVE);
            case CONTRE_INDICATION_TEMPORAIRE -> EnumSet.of(BILAN_EN_COURS, CONTRE_INDICATION_DEFINITIVE);
            case CONTRE_INDICATION_DEFINITIVE, GREFFE_REALISEE -> EnumSet.noneOf(StatutDuBilanPreGreffe.class);
        };
    }

    public boolean autorise(StatutDuBilanPreGreffe vers) {
        return this == vers || suivants().contains(vers);
    }
}
