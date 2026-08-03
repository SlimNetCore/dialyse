package com.hemodialyse.backend.domain.facturation.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.math.BigDecimal;

public record FacturationSettingsCommand(
        CenterId centerId,
        String userId,
        BigDecimal tvaRate,
        String codeFormat,
        boolean regroupementMultiForfait
) {
}

