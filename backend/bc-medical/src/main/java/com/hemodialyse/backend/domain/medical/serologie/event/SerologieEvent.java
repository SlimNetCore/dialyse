package com.hemodialyse.backend.domain.medical.serologie.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface SerologieEvent {

    UUID serologieId();

    OffsetDateTime at();

    /**
     * Déclenche l'alerte d'isolement machine côté cahier de dialyse (hors périmètre de ce module).
     */
    record SerologiePositiveDetectee(UUID serologieId, UUID patientId, OffsetDateTime at) implements SerologieEvent {
    }
}
