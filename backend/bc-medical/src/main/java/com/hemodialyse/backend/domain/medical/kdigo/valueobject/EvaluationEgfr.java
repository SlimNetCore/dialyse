package com.hemodialyse.backend.domain.medical.kdigo.valueobject;

import java.math.BigDecimal;

/**
 * Débit de filtration glomérulaire estimé (formule CKD-EPI 2021, sans coefficient ethnique) et
 * stade KDIGO correspondant.
 */
public record EvaluationEgfr(
        BigDecimal egfrMlMin173m2,
        StadeCkd stade,
        String referenceKdigo
) {
}
