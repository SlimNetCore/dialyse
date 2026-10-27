package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;

import java.time.LocalDate;

/**
 * Port sortant : les places d'un centre (colonnes de place de {@code patients}, ADR 0052). L'écriture passe par le verrou
 * {@link VerrouDesPlaces}, pris par le cas d'usage avant {@link #charger}.
 */
public interface PlacesDuCentreRepository {

    /**
     * Les occupations des patients actifs du centre, jugées au jour donné (jour du centre) : les patients sortis, les séjours
     * terminés ou pas encore commencés sont écartés (RG-PLN-010). Borné au centre.
     */
    PlacesDuCentre charger(CentreId centre, RessourcesDuCentre ressources, LocalDate jugeLe);

    /**
     * Le placement actuel de la fiche, même si le patient n'est plus actif ; vide si le patient est inconnu ou d'un autre centre.
     * Sans catégorie propre : le dépôt la déduit comme pour {@link #charger}.
     */
    java.util.Optional<Occupation> placementActuel(CentreId centre, Identifiants.PatientId patient);

    /** Écrit les {@link PlacesDuCentre#changements()} : seules les colonnes de place des patients concernés. */
    void enregistrer(PlacesDuCentre places);
}
