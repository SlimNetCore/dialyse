package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.VoletMedical;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.Optional;
import java.util.UUID;

public interface VoletMedicalRepositoryPort {
    Optional<VoletMedical> findBySeanceId(UUID seanceId, CenterId centerId);

    VoletMedical save(VoletMedical volet);
}

