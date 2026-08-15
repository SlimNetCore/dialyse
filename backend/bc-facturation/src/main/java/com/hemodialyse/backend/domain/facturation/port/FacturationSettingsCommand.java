package com.hemodialyse.backend.domain.facturation.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

public record FacturationSettingsCommand(
        CenterId centerId,
        String userId,
        String codeFormat,
        boolean regroupementMultiForfait
) {
}
