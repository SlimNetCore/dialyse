package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.stock.model.StockMovement;

public interface StockMovementRepositoryPort {
    StockMovement save(StockMovement movement);
}

