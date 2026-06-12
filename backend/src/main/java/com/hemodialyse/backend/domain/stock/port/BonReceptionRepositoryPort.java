package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonReception;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BonReceptionRepositoryPort {
    BonReception save(BonReception bon);

    Optional<BonReception> findById(UUID id, CenterId centerId);

    List<BonReception> findAll(CenterId centerId);
}

