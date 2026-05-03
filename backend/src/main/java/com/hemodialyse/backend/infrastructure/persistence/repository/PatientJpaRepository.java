package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.PatientJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientJpaRepository extends JpaRepository<PatientJpaEntity, UUID>, JpaSpecificationExecutor<PatientJpaEntity> {
    Optional<PatientJpaEntity> findByCenterIdAndNumeroAssurance(UUID centerId, String numeroAssurance);
    Optional<PatientJpaEntity> findByIdAndCenterId(UUID id, UUID centerId);
    List<PatientJpaEntity> findByCenterId(UUID centerId);
    long countByCenterId(UUID centerId);
}

