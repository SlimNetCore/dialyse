package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Port publié (lecture, planification.v1, LEC-PLN-GMAO, ADR 0179) : le déplacement temporaire enregistré pour un patient un jour donné
 * (maintenance d'un générateur, RG-PLN-105). Il prime sur la place habituelle ({@link PlacesDesPatients}) ; lu par la Séance à la validation
 * pour figer la place. Borné au centre ; un seul déplacement par centre, patient et date (le dernier enregistré remplace le précédent) ;
 * aucun cache.
 */
public interface DeplacementsDuJourDesPatients {

    /** Place provisoire d'un jour : salle, créneau et générateur choisis pour la séance de ce jour. */
    record DeplacementDuJour(UUID salle, UUID creneau, UUID generateur) {
    }

    /** Le déplacement du patient ce jour dans ce centre ; vide s'il n'y en a pas (ou s'il est d'un autre centre). */
    Optional<DeplacementDuJour> duPatient(UUID centre, UUID patient, LocalDate le);
}
