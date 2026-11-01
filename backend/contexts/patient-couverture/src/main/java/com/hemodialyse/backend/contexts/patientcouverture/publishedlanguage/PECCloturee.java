package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.util.UUID;

public record PECCloturee(UUID eventId, UUID patientId, UUID centreId, UUID pecId) {
}