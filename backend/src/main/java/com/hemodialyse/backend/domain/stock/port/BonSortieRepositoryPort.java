package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonSortie;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BonSortieRepositoryPort {
    BonSortie save(BonSortie bon);

    Optional<BonSortie> findById(UUID id, CenterId centerId);

    List<BonSortie> findAll(CenterId centerId);

    List<BonSortie> findBySeance(UUID seanceId, CenterId centerId);
}

