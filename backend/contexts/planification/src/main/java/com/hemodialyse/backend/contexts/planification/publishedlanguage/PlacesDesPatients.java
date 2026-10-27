package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Port publié (lecture) : la place des patients d'un centre. Remplacera les lectures SQL des colonnes de place par Séance,
 * Absences, Direction et Patient et couverture (ADR 0053). Bornée au centre ; aucun cache.
 */
public interface PlacesDesPatients {

    /** @param generateur nul pour une place héritée incomplète */
    record PlaceDuPatient(UUID patient, UUID salle, UUID creneau, UUID generateur, Set<DayOfWeek> jours) {
    }

    /** La place de la fiche, vide si le patient n'en a pas (ou n'est pas de ce centre). */
    Optional<PlaceDuPatient> deLaFiche(UUID centre, UUID patient);

    /** Les places des patients actifs du centre, jugées au jour donné (RG-PLN-010). */
    List<PlaceDuPatient> duCentre(UUID centre, LocalDate le);
}
