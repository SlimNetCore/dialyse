package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.util.UUID;

public record PECValidee(UUID eventId, UUID patientId, UUID centreId, UUID pecId, UUID forfaitEffectifId) {
}