package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.util.UUID;

public record SignSeanceMedecinRequest(
        UUID centerId,
        String userId
) {
}

