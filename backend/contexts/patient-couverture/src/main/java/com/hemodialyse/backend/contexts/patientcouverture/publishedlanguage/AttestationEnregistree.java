package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

public record AttestationEnregistree(UUID eventId, UUID patientId, UUID centreId, LocalDate periodeDebut,
                                     LocalDate periodeFin, LocalDate dateEdition) {
}