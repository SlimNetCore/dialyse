package com.hemodialyse.backend.domain.facturation.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.YearMonth;

public record FacturationDashboardQuery(CenterId centerId, YearMonth month) {
}

