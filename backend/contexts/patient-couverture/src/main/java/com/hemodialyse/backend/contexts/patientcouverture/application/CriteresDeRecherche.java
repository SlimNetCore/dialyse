package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;

import java.time.LocalDate;

/**
 * Critères de la recherche de patients (liste paginée). Page positive ou nulle, taille de 1 à 100 (décision du 02/11 : 400 au
 * lieu du plafond de 200 et des bornes silencieuses). Les filtres de colonne des référentiels portent le TEXTE recherché dans le
 * libellé affiché (le nom du champ reste l'identifiant de colonne).
 */
public record CriteresDeRecherche(int page, int size, String code, String nom, String prenom, String sexe,
                                  LocalDate dateAdmissionFrom, LocalDate dateAdmissionTo, String numeroAssurance,
                                  String etatPatient, Boolean nonFacturable, String medecinTraitantId, String positionId,
                                  String transporteurAllerId, String transporteurRetourId, String pecForfaitId, String sortBy,
                                  String sortDirection) {

    public static final int TAILLE_MAXIMALE = 100;

    public CriteresDeRecherche {
        if (page < 0) throw new SaisieInvalide("La page ne peut pas être négative");
        if (size < 1 || size > TAILLE_MAXIMALE) throw new SaisieInvalide("La taille doit être comprise entre 1 et " + TAILLE_MAXIMALE);
    }
}
