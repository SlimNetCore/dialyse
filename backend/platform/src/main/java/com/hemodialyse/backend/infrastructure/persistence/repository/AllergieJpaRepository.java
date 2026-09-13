package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.AllergieJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AllergieJpaRepository extends JpaRepository<AllergieJpaEntity, UUID> {

    List<AllergieJpaEntity> findByPatientIdAndCenterIdOrderByDateConstatationDesc(UUID patientId, UUID centerId);

    Page<AllergieJpaEntity> findByPatientIdAndCenterId(UUID patientId, UUID centerId, Pageable pageable);

    Optional<AllergieJpaEntity> findByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);

    void deleteByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);
}
