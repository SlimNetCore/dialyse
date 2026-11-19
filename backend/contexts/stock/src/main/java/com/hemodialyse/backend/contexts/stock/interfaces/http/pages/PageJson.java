package com.hemodialyse.backend.contexts.stock.interfaces.http.pages;

import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;

import java.util.List;
import java.util.function.Function;

/** Enveloppe JSON commune des listes paginées : {@code {items, total, page, size}} (comme {@code /referentiel/articles/page}). */
public record PageJson<T>(List<T> items, long total, int page, int size) {

    public static <S, T> PageJson<T> depuis(PageDeResultats<S> page, Function<? super S, ? extends T> conversion) {
        return new PageJson<>(page.items().stream().<T>map(conversion).toList(), page.total(), page.page(), page.size());
    }
}
