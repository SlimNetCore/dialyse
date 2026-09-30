package com.hemodialyse.backend.domain.migration.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Conversion des valeurs normalisées d'une ligne ({@link LegacyValueParser}) vers les types écrits en base.
 */
final class RowValues {

    private RowValues() {
    }

    static LocalDate date(EntityMigrator.Row row, String key) {
        String v = row.get(key);
        return v == null ? null : LocalDate.parse(v);
    }

    static BigDecimal decimal(EntityMigrator.Row row, String key) {
        String v = row.get(key);
        return v == null ? null : new BigDecimal(v);
    }

    static Integer integer(EntityMigrator.Row row, String key) {
        String v = row.get(key);
        return v == null ? null : Integer.valueOf(v);
    }

    static Boolean bool(EntityMigrator.Row row, String key) {
        String v = row.get(key);
        return v == null ? null : Boolean.valueOf(v);
    }

    /**
     * Map ordonnée acceptant les valeurs {@code null} (retirées ensuite par le migrateur).
     */
    static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) out.put((String) keyValues[i], keyValues[i + 1]);
        return out;
    }
}

