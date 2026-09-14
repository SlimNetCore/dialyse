package com.hemodialyse.backend.domain.medical.anemie.port;

import com.hemodialyse.backend.domain.medical.anemie.aggregate.AlerteObservance;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeAlerteObservance;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlerteObservanceRepositoryPort {

    List<AlerteObservance> findByPatientId(UUID patientId, CenterId centerId);

    /**
     * Alerte non résolue existante pour ce patient/type/nature — utilisée par le job planifié
     * pour éviter de dupliquer une alerte déjà ouverte pour la même non-conformité.
     */
    Optional<AlerteObservance> findActiveByPatientAndType(UUID patientId, CenterId centerId,
                                                          TypeTraitementAnemie typeTraitement,
                                                          TypeAlerteObservance type);

    Optional<AlerteObservance> findById(UUID alerteId, UUID patientId, CenterId centerId);

    AlerteObservance save(AlerteObservance alerte);
}
