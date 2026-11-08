package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PlaceDeSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Port sortant : la place où le patient dialyse un jour donné (Planification) : son déplacement temporaire de ce jour, à défaut la place
 * habituelle de sa fiche. Vide si le patient n'a pas de place complète (salle et créneau).
 */
public interface PlaceDuPatient {
    Optional<PlaceDeSeance> placeLe(CentreId centre, PatientId patient, LocalDate jour);
}
