package com.hemodialyse.backend.domain.medical.ordonnance.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface OrdonnanceEvent {

    UUID ordonnanceId();

    OffsetDateTime at();

    record OrdonnanceCreee(UUID ordonnanceId, UUID patientId, OffsetDateTime at) implements OrdonnanceEvent {
    }

    record OrdonnanceSignee(UUID ordonnanceId, UUID patientId, String numero,
                            OffsetDateTime at) implements OrdonnanceEvent {
    }

    record OrdonnanceAnnulee(UUID ordonnanceId, UUID patientId, OffsetDateTime at) implements OrdonnanceEvent {
    }
}
