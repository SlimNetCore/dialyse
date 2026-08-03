package com.hemodialyse.backend.domain.facturation.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.time.YearMonth;

public record FacturationPreviewQuery(
        CenterId centerId,
        YearMonth month,
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean regroupementMultiForfait
) {
}

