package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeSerologies;

import java.util.List;

/** Enveloppe de pagination de la plateforme : {@code items / total / page / size}. */
public record PageDeSerologiesReponse(List<SerologieReponse> items, long total, int page, int size) {
    public static PageDeSerologiesReponse de(PageDeSerologies page) {
        return new PageDeSerologiesReponse(page.items().stream().map(SerologieReponse::de).toList(), page.total(), page.page(), page.taille());
    }
}
