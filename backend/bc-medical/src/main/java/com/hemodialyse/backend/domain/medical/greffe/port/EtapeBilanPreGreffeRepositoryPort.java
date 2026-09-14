package com.hemodialyse.backend.domain.medical.greffe.port;

import com.hemodialyse.backend.domain.medical.greffe.aggregate.EtapeBilanPreGreffe;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EtapeBilanPreGreffeRepositoryPort {

    List<EtapeBilanPreGreffe> findByPatientId(UUID patientId, CenterId centerId);

    Optional<EtapeBilanPreGreffe> findById(UUID id, UUID patientId, CenterId centerId);

    EtapeBilanPreGreffe save(EtapeBilanPreGreffe etape);

    void deleteById(UUID id, UUID patientId, CenterId centerId);
}
