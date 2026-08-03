package com.hemodialyse.backend.domain.facturation.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FacturationValideeEvent(
        UUID centerId,
        int invoicesCount,
        int billedSeancesCount,
        BigDecimal totalTtc,
        OffsetDateTime occurredAt
) {
}

