package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDOrdonnances;

import java.util.List;

/** Enveloppe de pagination de la plateforme : {@code items / total / page / size}. */
public record PageDOrdonnancesReponse(List<OrdonnanceReponse> items, long total, int page, int size) {
    public static PageDOrdonnancesReponse de(PageDOrdonnances page) {
        return new PageDOrdonnancesReponse(page.items().stream().map(OrdonnanceReponse::de).toList(), page.total(), page.page(),
                page.taille());
    }
}
