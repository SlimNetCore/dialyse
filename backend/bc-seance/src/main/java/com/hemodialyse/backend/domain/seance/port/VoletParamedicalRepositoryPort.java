package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.VoletParamedical;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.Optional;
import java.util.UUID;

public interface VoletParamedicalRepositoryPort {
    Optional<VoletParamedical> findBySeanceId(UUID seanceId, CenterId centerId);

    VoletParamedical save(VoletParamedical volet);
}

