package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.*;

import java.util.List;
import java.util.UUID;

public interface StockDashboardPort {
    List<StockTrendPoint> mouvementTrend(CenterId centerId, int days);

    List<StockTopArticlePoint> topArticlesByMovement(CenterId centerId, int days, int topN, StockTopSort sortBy);

    List<StockValoriseItem> stockValorise(CenterId centerId);

    List<TracabiliteItem> tracabiliteByLot(CenterId centerId, UUID lotId);

    List<AlerteStock> alertes(CenterId centerId, int joursAvantPeremption);
}

