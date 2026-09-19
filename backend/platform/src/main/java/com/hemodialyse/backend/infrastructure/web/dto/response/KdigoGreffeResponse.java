package com.hemodialyse.backend.infrastructure.web.dto.response;

import java.util.List;

public record KdigoGreffeResponse(
        EvaluationRisqueKdigoResponse risqueImmunologique,
        EvaluationEgfrResponse fonctionRenale,
        List<AlerteSerologieKdigoResponse> alertesSerologiques
) {
}
