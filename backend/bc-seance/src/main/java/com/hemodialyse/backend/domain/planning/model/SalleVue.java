package com.hemodialyse.backend.domain.planning.model;

import java.util.List;
import java.util.UUID;

/**
 * Vue d'une salle : ses générateurs affectés et sa capacité (nombre maximal de générateurs, absente = illimitée).
 */
public record SalleVue(UUID id, String code, String nom, boolean isolement, Integer capacite,
                       List<GenerateurVue> generateurs) {

    public SalleVue {
        generateurs = generateurs == null ? List.of() : List.copyOf(generateurs);
    }

    public int nbGenerateurs() {
        return generateurs.size();
    }

    /**
     * Places encore disponibles ; {@code null} si la capacité n'est pas limitée.
     */
    public Integer placesRestantes() {
        return capacite == null ? null : capacite - nbGenerateurs();
    }

    /**
     * Vrai si la salle porte plus de générateurs que sa capacité (capacité réduite après affectation).
     */
    public boolean depassement() {
        return capacite != null && nbGenerateurs() > capacite;
    }

    /**
     * Générateur affecté à la salle ({@code statut} : statut GMAO, ex. EN_SERVICE ou HORS_SERVICE).
     */
    public record GenerateurVue(UUID id, String code, String designation, String statut) {
    }
}
