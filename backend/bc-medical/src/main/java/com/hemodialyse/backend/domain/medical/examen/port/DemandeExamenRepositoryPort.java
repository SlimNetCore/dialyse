package com.hemodialyse.backend.domain.medical.examen.port;

import com.hemodialyse.backend.domain.medical.examen.aggregate.DemandeExamen;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.Optional;
import java.util.UUID;

public interface DemandeExamenRepositoryPort {

    PagedResult<DemandeExamen> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size);

    Optional<DemandeExamen> findById(UUID id, UUID patientId, CenterId centerId);

    DemandeExamen save(DemandeExamen demande);
}
