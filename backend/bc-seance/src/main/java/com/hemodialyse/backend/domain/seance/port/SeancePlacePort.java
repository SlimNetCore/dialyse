package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.PlaceSeance;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de lecture de la place d'un patient à une date : son déplacement temporaire de ce jour s'il en a un, sinon sa
 * place habituelle ; vide si le patient n'est pas placé.
 */
public interface SeancePlacePort {

    Optional<PlaceSeance> placeLe(CenterId centerId, UUID patientId, LocalDate date);
}
