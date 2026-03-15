package com.hemodialyse.backend.domain.pec.port;

import com.hemodialyse.backend.domain.pec.model.PriseEnCharge;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port Out — PEC persistence.
 */
public interface PecRepositoryPort {
    PriseEnCharge save(PriseEnCharge pec);
    Optional<PriseEnCharge> findById(UUID id, CenterId centerId);
    List<PriseEnCharge> findByPatient(CenterId centerId, UUID patientId);
    List<PriseEnCharge> findByCenter(CenterId centerId);
}

