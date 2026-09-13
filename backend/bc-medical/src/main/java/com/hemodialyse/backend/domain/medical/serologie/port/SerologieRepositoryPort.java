package com.hemodialyse.backend.domain.medical.serologie.port;

import com.hemodialyse.backend.domain.medical.serologie.aggregate.Serologie;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.MarqueurSerologique;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SerologieRepositoryPort {

    List<Serologie> findByPatientId(UUID patientId, CenterId centerId);

    PagedResult<Serologie> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size);

    Optional<Serologie> findById(UUID id, UUID patientId, CenterId centerId);

    boolean existsByPatientIdAndMarqueurAndDatePrelevement(UUID patientId, CenterId centerId,
                                                           MarqueurSerologique marqueur, LocalDate datePrelevement);

    Serologie save(Serologie serologie);

    void deleteById(UUID id, UUID patientId, CenterId centerId);
}
