package com.hemodialyse.backend.domain.medical.observation.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface ObservationEvent {

    UUID observationId();

    OffsetDateTime at();

    record ObservationEnregistree(UUID observationId, UUID patientId, OffsetDateTime at) implements ObservationEvent {
    }
}
