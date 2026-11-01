package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

public record PatientAdmis(UUID eventId, UUID patientId, UUID centreId, LocalDate dateAdmission) {
}