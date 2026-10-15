package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;

import java.util.List;
import java.util.function.Function;

/**
 * Page JSON : mêmes champs que le contrat observé ({@code items}, {@code total}, {@code page}, {@code size}).
 */
public record PageReponse<T>(List<T> items, long total, int page, int size) {

    static <S, T> PageReponse<T> depuis(PageDe<S> page, Function<S, T> vers) {
        return new PageReponse<>(page.items().stream().map(vers).toList(), page.total(), page.page(), page.size());
    }
}
