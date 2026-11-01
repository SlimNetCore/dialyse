package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

public record SeanceCreee(UUID eventId, UUID seanceId, UUID centreId, UUID patientId, UUID pecId,
                          LocalDate dateSeance, String typeSeance) {
}