package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonCommande;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BonCommandeRepositoryPort {
    BonCommande save(BonCommande bon);

    Optional<BonCommande> findById(UUID id, CenterId centerId);

    List<BonCommande> findAll(CenterId centerId);
}

