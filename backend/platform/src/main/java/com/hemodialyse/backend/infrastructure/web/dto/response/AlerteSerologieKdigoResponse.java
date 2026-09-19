package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.kdigo.valueobject.AlerteSerologieKdigo;

public record AlerteSerologieKdigoResponse(
        String marqueur,
        String resultat,
        String message,
        String referenceKdigo
) {

    public static AlerteSerologieKdigoResponse from(AlerteSerologieKdigo a) {
        return new AlerteSerologieKdigoResponse(a.marqueur(), a.resultat(), a.message(), a.referenceKdigo());
    }
}
