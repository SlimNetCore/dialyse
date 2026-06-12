package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.VoletMedicalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VoletMedicalJpaRepository extends JpaRepository<VoletMedicalJpaEntity, UUID> {
    Optional<VoletMedicalJpaEntity> findBySeanceIdAndCenterId(UUID seanceId, UUID centerId);
}

