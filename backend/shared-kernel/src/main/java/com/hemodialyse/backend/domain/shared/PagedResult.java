package com.hemodialyse.backend.domain.shared;

import java.util.List;

/**
 * Generic domain-level pagination result (no Spring/JPA dependency — AGENTS.md §3).
 * Used by domain ports and use cases to return paged data.
 *
 * @param <T> type of the items
 */
public record PagedResult<T>(
        List<T> items,
        long total,
        int page,
        int size
) {
    /**
     * Convenience factory.
     */
    public static <T> PagedResult<T> of(List<T> items, long total, int page, int size) {
        return new PagedResult<>(items, total, page, size);
    }
}

