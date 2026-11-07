package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Les horodatages du contrat HTTP sont en UTC. */
final class Horodatages {
    private Horodatages() {
    }

    static OffsetDateTime horodatage(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
