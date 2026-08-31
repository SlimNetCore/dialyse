package com.hemodialyse.backend.domain.facturation.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FacturationPreviewSeance(
        UUID seanceId,
        LocalDate seanceDate,
        String seanceStatus,
        UUID forfaitId,
        String forfaitLabel,
        BigDecimal forfaitPrixHt
) {
}

