package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface SeanceForfaitCatalogPort {
    Optional<SeanceForfaitSnapshot> findById(CenterId centerId, UUID forfaitId);

    record SeanceForfaitSnapshot(UUID id, String code, String nom, BigDecimal prix) {
    }
}

