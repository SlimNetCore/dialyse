package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.Instant;
import java.util.UUID;

public record IssueLicenseRequest(UUID centerId, String type, int maxUsers, Instant validFrom, Instant validUntil) {
}
