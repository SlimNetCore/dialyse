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

    /**
     * Les patients dont le générateur de la fiche est celui-ci, avec ou sans jours, salle ou créneau (place héritée incomplète
     * comprise), hors patients sortis jugés au jour donné (RG-PLN-010) ; triés par identifiant. Borné au centre. Sert à citer les
     * patients de l'alerte « générateur indisponible » (KB-PLN-63).
     */
    java.util.List<Identifiants.PatientId> patientsDuGenerateur(CentreId centre, Identifiants.GenerateurId generateur, LocalDate jugeLe);

    /** Écrit les {@link PlacesDuCentre#changements()} : seules les colonnes de place des patients concernés. */
    void enregistrer(PlacesDuCentre places);
}
