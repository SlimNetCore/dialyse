package com.hemodialyse.backend.contexts.seance.interfaces.http.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Corps de {@code POST /api/v1/seances/{id}/valider} : mêmes champs JSON que le legacy. {@code userId} est le signataire (KB-SEA-11,
 * reproduit : lu dans le corps, jamais dans la session) ; {@code consommations} les consommables relevés (peut être absent).
 */
public record ValidationDeSeanceDemande(UUID centerId, String userId, List<ConsommationRelevee> consommations) {

    public record ConsommationRelevee(UUID articleId, BigDecimal quantite) {
    }
}
