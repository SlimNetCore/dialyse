package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.AlerteStock;
import com.hemodialyse.backend.domain.stock.model.StockValoriseItem;
import com.hemodialyse.backend.domain.stock.model.TracabiliteItem;

import java.util.List;
import java.util.UUID;

public interface StockDashboardPort {
    List<StockValoriseItem> stockValorise(CenterId centerId);

    List<TracabiliteItem> tracabiliteByLot(CenterId centerId, UUID lotId);

    List<AlerteStock> alertes(CenterId centerId, int joursAvantPeremption);
}

