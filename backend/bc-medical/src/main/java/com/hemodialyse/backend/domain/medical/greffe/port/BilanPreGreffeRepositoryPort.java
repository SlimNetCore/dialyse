package com.hemodialyse.backend.domain.medical.greffe.port;

import com.hemodialyse.backend.domain.medical.greffe.aggregate.BilanPreGreffe;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.Optional;
import java.util.UUID;

public interface BilanPreGreffeRepositoryPort {

    Optional<BilanPreGreffe> findByPatientId(UUID patientId, CenterId centerId);

    BilanPreGreffe save(BilanPreGreffe bilan);
}
