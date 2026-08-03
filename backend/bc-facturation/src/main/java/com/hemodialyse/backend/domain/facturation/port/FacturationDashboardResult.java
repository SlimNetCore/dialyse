package com.hemodialyse.backend.domain.facturation.port;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public record FacturationDashboardResult(
        UUID centerId,
        YearMonth month,
        BigDecimal revenueTtc,
        BigDecimal revenueHt,
        long billedSeances,
        long billedPatients,
        long createdInvoices,
        List<FacturationDashboardBucket> byInsurance,
        List<FacturationDashboardStatusBucket> byPatientStatus
) {
}

