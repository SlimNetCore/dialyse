package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record UpsertBilanImmunologiqueRequest(
        UUID centerId,
        String groupeSanguinConfirme,
        String typageHla,
        BigDecimal praClasseI,
        BigDecimal praClasseII
) {
}
