package com.hemodialyse.backend.domain.stock.model;

/**
 * Supported sorting modes for top articles in dashboard analytics.
 */
public enum StockTopSort {
    VALUE,
    QUANTITY;

    public static StockTopSort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return VALUE;
        }
        try {
            return StockTopSort.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return VALUE;
        }
    }
}

