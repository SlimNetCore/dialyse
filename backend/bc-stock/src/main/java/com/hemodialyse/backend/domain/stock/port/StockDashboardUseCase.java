package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.*;

import java.util.List;
import java.util.UUID;

/**
 * Primary port: stock dashboards & traceability.
 */
public interface StockDashboardUseCase {
    StockDashboardAnalytics analytics(CenterId centerId, int days, int topN, StockTopSort sortBy);

    List<StockValoriseItem> stockValorise(CenterId centerId);

    List<TracabiliteItem> tracabilite(CenterId centerId, UUID lotId);

    List<AlerteStock> alertes(CenterId centerId);

    PmpExplanation pmpExplain(CenterId centerId, UUID articleId);
}


