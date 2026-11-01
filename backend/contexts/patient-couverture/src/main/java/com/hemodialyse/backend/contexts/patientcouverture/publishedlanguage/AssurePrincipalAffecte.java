package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

public record AssurePrincipalAffecte(UUID eventId, UUID patientId, UUID centreId, String assureId) {
}