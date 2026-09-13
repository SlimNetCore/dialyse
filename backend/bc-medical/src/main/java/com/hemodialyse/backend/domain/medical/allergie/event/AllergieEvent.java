package com.hemodialyse.backend.domain.medical.allergie.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface AllergieEvent {

    UUID allergieId();

    OffsetDateTime at();

    record AllergieDeclaree(UUID allergieId, UUID patientId, OffsetDateTime at) implements AllergieEvent {
    }

    /**
     * Déclenche le bandeau d'alerte permanent du dossier médical (criticité HAUTE).
     */
    record AllergieCritiqueDeclaree(UUID allergieId, UUID patientId, OffsetDateTime at) implements AllergieEvent {
    }
}
