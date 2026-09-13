package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.util.UUID;

public record UpdateAllergieRequest(
        UUID centerId,
        String criticite,
        String manifestations,
        String statutVerification
) {
}
