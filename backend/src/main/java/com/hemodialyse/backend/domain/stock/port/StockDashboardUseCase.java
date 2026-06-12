package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.AlerteStock;
import com.hemodialyse.backend.domain.stock.model.PmpExplanation;
import com.hemodialyse.backend.domain.stock.model.StockValoriseItem;
import com.hemodialyse.backend.domain.stock.model.TracabiliteItem;

import java.util.List;
import java.util.UUID;

/**
 * Primary port: stock dashboards & traceability.
 */
public interface StockDashboardUseCase {
    List<StockValoriseItem> stockValorise(CenterId centerId);

    List<TracabiliteItem> tracabilite(CenterId centerId, UUID lotId);

    List<AlerteStock> alertes(CenterId centerId);

    PmpExplanation pmpExplain(CenterId centerId, UUID articleId);
}


