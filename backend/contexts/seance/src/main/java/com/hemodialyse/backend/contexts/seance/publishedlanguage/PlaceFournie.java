package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié (fourni à la Séance par la plateforme, SE2) : la place où le patient dialyse un jour donné, déplacement temporaire de ce
 * jour d'abord, place habituelle de sa fiche sinon (Planification). Vide sans salle ni créneau.
 */
public interface PlaceFournie {

    Optional<Place> placeLe(UUID centre, UUID patient, LocalDate jour);

    /** Salle et créneau obligatoires, générateur facultatif. */
    record Place(UUID salle, UUID creneau, UUID generateur) {
    }
}
