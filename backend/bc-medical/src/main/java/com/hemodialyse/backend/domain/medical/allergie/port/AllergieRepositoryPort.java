package com.hemodialyse.backend.domain.medical.allergie.port;

import com.hemodialyse.backend.domain.medical.allergie.aggregate.Allergie;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AllergieRepositoryPort {

    List<Allergie> findByPatientId(UUID patientId, CenterId centerId);

    PagedResult<Allergie> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size);

    Optional<Allergie> findById(UUID id, UUID patientId, CenterId centerId);

    Allergie save(Allergie allergie);

    void deleteById(UUID id, UUID patientId, CenterId centerId);
}
