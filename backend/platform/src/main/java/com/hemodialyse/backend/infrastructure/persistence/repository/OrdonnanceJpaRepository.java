package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.OrdonnanceJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrdonnanceJpaRepository extends JpaRepository<OrdonnanceJpaEntity, UUID> {

    Page<OrdonnanceJpaEntity> findByPatientIdAndCenterId(UUID patientId, UUID centerId, Pageable pageable);

    Optional<OrdonnanceJpaEntity> findByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);
}
