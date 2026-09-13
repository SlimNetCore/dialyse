package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.SerologieJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SerologieJpaRepository extends JpaRepository<SerologieJpaEntity, UUID> {

    List<SerologieJpaEntity> findByPatientIdAndCenterIdOrderByDatePrelevementDesc(UUID patientId, UUID centerId);

    Page<SerologieJpaEntity> findByPatientIdAndCenterId(UUID patientId, UUID centerId, Pageable pageable);

    Optional<SerologieJpaEntity> findByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);

    boolean existsByPatientIdAndCenterIdAndMarqueurAndDatePrelevement(UUID patientId, UUID centerId,
                                                                      String marqueur, LocalDate datePrelevement);

    void deleteByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);
}
