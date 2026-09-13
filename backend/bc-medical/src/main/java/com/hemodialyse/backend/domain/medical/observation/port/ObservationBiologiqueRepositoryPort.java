package com.hemodialyse.backend.domain.medical.observation.port;

import com.hemodialyse.backend.domain.medical.observation.aggregate.ObservationBiologique;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ObservationBiologiqueRepositoryPort {

    PagedResult<ObservationBiologique> findPagedByPatientId(UUID patientId, CenterId centerId,
                                                            String loincCode, LocalDate from, LocalDate to,
                                                            int page, int size);

    List<ObservationBiologique> findByDemandeExamenId(UUID demandeExamenId, CenterId centerId);

    Optional<ObservationBiologique> findById(UUID id, UUID patientId, CenterId centerId);

    ObservationBiologique save(ObservationBiologique observation);

    void deleteById(UUID id, UUID patientId, CenterId centerId);
}
