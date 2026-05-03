package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.util.Map;
import java.util.UUID;

public record PrintRequest(
        UUID centerId,
        String typeDocument,
        String formatOverride,       // null = utilise le format du modèle
        Map<String, String> params   // patientId, pecId, etc.
) {
}

