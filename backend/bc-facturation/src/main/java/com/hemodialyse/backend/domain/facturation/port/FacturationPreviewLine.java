package com.hemodialyse.backend.domain.facturation.port;

import java.math.BigDecimal;
import java.util.UUID;

public record FacturationPreviewLine(
        UUID forfaitId,
        String forfaitLabel,
        BigDecimal unitPriceHt,
        int seanceCount,
        BigDecimal lineHt
) {
}

