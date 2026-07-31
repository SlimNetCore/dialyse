package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

/**
 * Generates auto-incremented, prefixed references (BL-00001, BR-00001, BS-00001)
 * per center using the app_settings table. Implementation must be transactional
 * and lock the counter row to be concurrency safe.
 */
public interface StockSequencePort {
    String next(CenterId centerId, String cle);
}

