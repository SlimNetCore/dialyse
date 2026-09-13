package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.kdigo.valueobject.EvaluationCible;

import java.math.BigDecimal;

public record EvaluationCibleResponse(
        String code,
        BigDecimal valeur,
        String unite,
        String statut,
        BigDecimal borneMin,
        BigDecimal borneMax,
        String referenceKdigo
) {

    public static EvaluationCibleResponse from(EvaluationCible e) {
        return new EvaluationCibleResponse(e.code(), e.valeur(), e.unite(), e.statut().name(),
                e.borneMin(), e.borneMax(), e.referenceKdigo());
    }
}
