package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.List;
import java.util.function.Function;

/**
 * Enveloppe de pagination exposée par l'API (AGENTS.md §9 : {@code items/total/page/size}).
 *
 * @param <T> type des éléments déjà projetés en DTO de réponse
 */
public record PagedResponse<T>(
        List<T> items,
        long total,
        int page,
        int size
) {

    /**
     * Projette un {@link PagedResult} du domaine vers l'enveloppe HTTP.
     */
    public static <D, R> PagedResponse<R> from(PagedResult<D> result, Function<D, R> mapper) {
        return new PagedResponse<>(
                result.items().stream().map(mapper).toList(),
                result.total(),
                result.page(),
                result.size()
        );
    }
}
