package com.hemodialyse.backend.contexts.stock.interfaces.http.dto;

import com.hemodialyse.backend.contexts.stock.domain.consommations.ConsommationDeSeance;

import java.util.List;
import java.util.UUID;

/**
 * Consommation d'une séance avec ses imputations.
 */
public record ConsommationReponse(UUID seanceId, UUID patientId, UUID sortieId, List<ImputationReponse> imputations) {
    public static ConsommationReponse depuis(ConsommationDeSeance c) {
        return new ConsommationReponse(c.seanceId(), c.patientId(), c.sortieId(), c.imputations().stream()
                .map(i -> new ImputationReponse(i.id(), i.articleId(), i.lotId(), i.quantite(), i.alerte())).toList());
    }
}
