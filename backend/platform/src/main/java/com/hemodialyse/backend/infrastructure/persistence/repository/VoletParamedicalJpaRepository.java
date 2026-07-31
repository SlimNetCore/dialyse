package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.VoletParamedicalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VoletParamedicalJpaRepository extends JpaRepository<VoletParamedicalJpaEntity, UUID> {
    Optional<VoletParamedicalJpaEntity> findBySeanceIdAndCenterId(UUID seanceId, UUID centerId);
}

