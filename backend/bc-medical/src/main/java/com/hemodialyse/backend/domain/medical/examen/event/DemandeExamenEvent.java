package com.hemodialyse.backend.domain.medical.examen.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface DemandeExamenEvent {

    UUID demandeExamenId();

    OffsetDateTime at();

    record DemandeExamenEmise(UUID demandeExamenId, UUID patientId, OffsetDateTime at) implements DemandeExamenEvent {
    }

    record ResultatExamenRecu(UUID demandeExamenId, UUID patientId, OffsetDateTime at) implements DemandeExamenEvent {
    }
}
