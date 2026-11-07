package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeFiches;

import java.util.List;
import java.util.function.Function;

/** Enveloppe de pagination de la plateforme : {@code items / total / page / size}. */
public record PageReponse<T>(List<T> items, long total, int page, int size) {
    public static <F, T> PageReponse<T> de(PageDeFiches<F> page, Function<F, T> reponse) {
        return new PageReponse<>(page.items().stream().map(reponse).toList(), page.total(), page.page(), page.taille());
    }
}
