package com.hemodialyse.backend.domain.medical.ordonnance.port;

import com.hemodialyse.backend.domain.medical.ordonnance.aggregate.Ordonnance;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.Optional;
import java.util.UUID;

public interface OrdonnanceRepositoryPort {

    PagedResult<Ordonnance> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size);

    Optional<Ordonnance> findById(UUID id, UUID patientId, CenterId centerId);

    Ordonnance save(Ordonnance ordonnance);
}
