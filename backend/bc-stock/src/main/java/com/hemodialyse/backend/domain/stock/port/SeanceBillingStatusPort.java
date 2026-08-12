package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.UUID;

/**
 * Outbound port used by stock domain to know if a seance is already billed.
 */
public interface SeanceBillingStatusPort {
    boolean isBilled(CenterId centerId, UUID seanceId);
}

