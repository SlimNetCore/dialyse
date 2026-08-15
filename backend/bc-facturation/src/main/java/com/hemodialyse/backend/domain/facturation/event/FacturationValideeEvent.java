package com.hemodialyse.backend.domain.facturation.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FacturationValideeEvent(
        UUID centerId,
        LocalDate periodStart,
        LocalDate periodEnd,
        int invoicesCount,
        int billedSeancesCount,
        BigDecimal totalTtc,
        OffsetDateTime occurredAt
) {
}

