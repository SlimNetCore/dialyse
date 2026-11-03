package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;

/**
 * Critères de la recherche paginée des PEC du centre (mêmes filtres que l'existant). Page positive ou nulle, taille de 1 à 200
 * (400 sinon). {@code debut} et {@code fin} portent sur les dates DEMANDÉES : date exacte ou intervalle {@code a..b}, formats
 * {@code yyyy-MM-dd} ou {@code dd/MM/yyyy}, une saisie illisible est ignorée en silence (reproduit).
 */
public record CriteresDePec(int page, int size, String search, String code, String nom, String assurance, String debut, String fin,
                            String statut) {

    public CriteresDePec {
        if (page < 0) throw new SaisieInvalide("La page ne peut pas être négative");
        if (size < 1 || size > 200) throw new SaisieInvalide("La taille doit être comprise entre 1 et 200");
    }
}
