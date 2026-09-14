package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.BilanPreGreffeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BilanPreGreffeJpaRepository extends JpaRepository<BilanPreGreffeJpaEntity, UUID> {

    Optional<BilanPreGreffeJpaEntity> findByPatientIdAndCenterId(UUID patientId, UUID centerId);
}
