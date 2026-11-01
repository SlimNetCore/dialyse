package com.hemodialyse.backend.contexts.seance.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CreerSeanceRequest(UUID patientId, UUID centreId, UUID pecId, LocalDate dateSeance, String typeSeance) {
}
