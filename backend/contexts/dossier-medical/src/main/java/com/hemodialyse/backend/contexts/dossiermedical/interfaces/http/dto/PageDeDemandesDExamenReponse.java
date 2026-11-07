package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeDemandesDExamen;

import java.util.List;

/** Enveloppe de pagination de la plateforme : {@code items / total / page / size}. */
public record PageDeDemandesDExamenReponse(List<DemandeDExamenReponse> items, long total, int page, int size) {
    public static PageDeDemandesDExamenReponse de(PageDeDemandesDExamen page) {
        return new PageDeDemandesDExamenReponse(page.items().stream().map(DemandeDExamenReponse::de).toList(), page.total(), page.page(),
                page.taille());
    }
}
