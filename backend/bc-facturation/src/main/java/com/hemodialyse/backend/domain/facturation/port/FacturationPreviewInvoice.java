package com.hemodialyse.backend.domain.facturation.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FacturationPreviewInvoice(
        String previewKey,
        UUID patientId,
        String patientCode,
        String patientFullName,
        String patientStatusSnapshot,
        BigDecimal totalHt,
        BigDecimal totalTva,
        BigDecimal totalTtc,
        List<FacturationPreviewLine> lines,
        List<UUID> seanceIds,
        List<FacturationPreviewSeance> seances
) {
}

