package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesPrescriptionsDuPatient;

import java.util.List;

/** Enveloppe de pagination de la plateforme : {@code items / total / page / size}. */
public record PageDePrescriptionsReponse(List<PrescriptionReponse> items, long total, int page, int size) {
    public static PageDePrescriptionsReponse de(ConsulterLesPrescriptionsDuPatient.Page page) {
        return new PageDePrescriptionsReponse(page.items().stream().map(PrescriptionReponse::de).toList(), page.total(), page.page(),
                page.taille());
    }
}
