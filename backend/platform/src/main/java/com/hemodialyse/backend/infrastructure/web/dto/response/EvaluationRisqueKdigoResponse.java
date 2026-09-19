package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.kdigo.valueobject.EvaluationRisqueKdigo;

import java.math.BigDecimal;

public record EvaluationRisqueKdigoResponse(
        String code,
        BigDecimal valeur,
        String unite,
        String niveau,
        String referenceKdigo
) {

    public static EvaluationRisqueKdigoResponse from(EvaluationRisqueKdigo e) {
        return new EvaluationRisqueKdigoResponse(e.code(), e.valeur(), e.unite(), e.niveau().name(), e.referenceKdigo());
    }
}
