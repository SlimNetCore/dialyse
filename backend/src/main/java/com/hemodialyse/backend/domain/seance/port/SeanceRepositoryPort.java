package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.Optional;
import java.util.UUID;

public interface SeanceRepositoryPort {
    Seance save(Seance seance);

    Optional<Seance> findById(UUID seanceId, CenterId centerId);
}

