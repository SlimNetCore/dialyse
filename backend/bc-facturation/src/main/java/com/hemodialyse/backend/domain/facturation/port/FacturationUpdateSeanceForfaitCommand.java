package com.hemodialyse.backend.domain.facturation.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

public record FacturationUpdateSeanceForfaitCommand(
        CenterId centerId,
        String userId,
        UUID seanceId,
        UUID forfaitId,
        YearMonth month,
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean regroupementMultiForfait
) {
}

