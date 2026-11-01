package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

public record EnregistrerRequest(UUID patientId, UUID centreId, LocalDate dateDebut, LocalDate dateFin) {
}
