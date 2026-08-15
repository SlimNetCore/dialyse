package com.hemodialyse.backend.domain.facturation.valueobject;

import java.time.OffsetDateTime;

/**
 * Paramètres de facturation d'un centre.
 * Le taux de TVA est désormais géré exclusivement via les types de TVA (TypeTVA).
 */
public record ParametresFacturation(
        String codeFormat,
        boolean regroupementMultiForfait,
        OffsetDateTime updatedAt
) {
    public ParametresFacturation {
        if (codeFormat == null || codeFormat.isBlank()) {
            throw new IllegalArgumentException("Le code de facturation est obligatoire");
        }
    }

    public static ParametresFacturation defaults() {
        return new ParametresFacturation("FAC-{YEAR}-{SEQ}", true, null);
    }
}