package com.hemodialyse.backend.domain.facturation.port;

import com.hemodialyse.backend.domain.facturation.valueobject.FacturationPeriod;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SeanceFacturationPort {
    List<SeanceFacturationCandidate> findEligibleSeances(CenterId centerId, FacturationPeriod period);

    void markAsAbsent(CenterId centerId, UUID seanceId, String userId);

    void overrideForfait(CenterId centerId, UUID seanceId, UUID forfaitId, String userId);

    void markAsBilled(CenterId centerId, Map<UUID, UUID> seanceToFactureId);

    FacturationDashboardResult loadDashboard(CenterId centerId, java.time.YearMonth month);
}

