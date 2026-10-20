package com.hemodialyse.backend.domain.facturation.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

public interface SeanceFacturationPort {
    FacturationDashboardResult loadDashboard(CenterId centerId, java.time.YearMonth month);
}
