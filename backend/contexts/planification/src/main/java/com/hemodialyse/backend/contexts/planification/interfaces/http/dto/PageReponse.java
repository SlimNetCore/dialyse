package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;

import java.util.List;
import java.util.function.Function;

/**
 * Page de résultats : {@code size} est la taille effective (plafonnée), {@code total} le nombre sans pagination.
 */
public record PageReponse<T>(List<T> items, int page, int size, long total) {
    public static <S, T> PageReponse<T> depuis(PageDeResultats<S> page, Function<S, T> vers) {
        return new PageReponse<>(page.elements().stream().map(vers).toList(), page.page(), page.taille(), page.total());
    }
}
