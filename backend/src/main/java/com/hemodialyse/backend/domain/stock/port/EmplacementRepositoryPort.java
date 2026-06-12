package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Emplacement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmplacementRepositoryPort {
    Emplacement save(Emplacement emplacement);

    Optional<Emplacement> findById(UUID id, CenterId centerId);

    List<Emplacement> findAllActive(CenterId centerId);
}

