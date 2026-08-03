package com.hemodialyse.backend.domain.facturation.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

public record ParametresFacturation(
        BigDecimal tvaRate,
        String codeFormat,
        boolean regroupementMultiForfait,
        OffsetDateTime updatedAt
) {
    public ParametresFacturation {
        if (tvaRate == null) {
            throw new IllegalArgumentException("Le taux de TVA est obligatoire");
        }
        if (tvaRate.compareTo(BigDecimal.ZERO) < 0 || tvaRate.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Le taux de TVA doit etre entre 0 et 100");
        }
        if (codeFormat == null || codeFormat.isBlank()) {
            throw new IllegalArgumentException("Le code de facturation est obligatoire");
        }
    }

    public static ParametresFacturation defaults() {
        return new ParametresFacturation(new BigDecimal("19.00"), "FAC-{YEAR}-{SEQ}", true, null);
    }

    public BigDecimal tvaRatio() {
        return tvaRate.movePointLeft(2).setScale(6, RoundingMode.HALF_UP);
    }
}

