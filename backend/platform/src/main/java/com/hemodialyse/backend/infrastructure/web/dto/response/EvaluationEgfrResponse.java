package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.kdigo.valueobject.EvaluationEgfr;

import java.math.BigDecimal;

public record EvaluationEgfrResponse(
        BigDecimal egfrMlMin173m2,
        String stade,
        String referenceKdigo
) {

    public static EvaluationEgfrResponse from(EvaluationEgfr e) {
        return new EvaluationEgfrResponse(e.egfrMlMin173m2(), e.stade().name(), e.referenceKdigo());
    }
}
