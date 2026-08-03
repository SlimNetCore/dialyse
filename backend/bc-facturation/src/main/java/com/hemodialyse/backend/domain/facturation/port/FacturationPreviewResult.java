package com.hemodialyse.backend.domain.facturation.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record FacturationPreviewResult(
        UUID centerId,
        OffsetDateTime generatedAt,
        LocalDate periodStart,
        LocalDate periodEnd,
        int totalFactures,
        BigDecimal totalHt,
        BigDecimal totalTva,
        BigDecimal totalTtc,
        List<FacturationPreviewInvoice> invoices
) {
}

