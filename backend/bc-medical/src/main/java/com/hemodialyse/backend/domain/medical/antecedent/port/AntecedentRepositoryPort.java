package com.hemodialyse.backend.domain.medical.antecedent.port;

import com.hemodialyse.backend.domain.medical.antecedent.aggregate.Antecedent;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AntecedentRepositoryPort {

    List<Antecedent> findByPatientId(UUID patientId, CenterId centerId);

    PagedResult<Antecedent> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size);

    Optional<Antecedent> findById(UUID id, UUID patientId, CenterId centerId);

    Antecedent save(Antecedent antecedent);

    void deleteById(UUID id, UUID patientId, CenterId centerId);
}
