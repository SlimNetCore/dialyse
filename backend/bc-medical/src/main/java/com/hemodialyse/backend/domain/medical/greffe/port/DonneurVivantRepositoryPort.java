package com.hemodialyse.backend.domain.medical.greffe.port;

import com.hemodialyse.backend.domain.medical.greffe.aggregate.DonneurVivant;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DonneurVivantRepositoryPort {

    List<DonneurVivant> findByPatientId(UUID patientId, CenterId centerId);

    Optional<DonneurVivant> findById(UUID id, UUID patientId, CenterId centerId);

    DonneurVivant save(DonneurVivant donneur);

    void deleteById(UUID id, UUID patientId, CenterId centerId);
}
