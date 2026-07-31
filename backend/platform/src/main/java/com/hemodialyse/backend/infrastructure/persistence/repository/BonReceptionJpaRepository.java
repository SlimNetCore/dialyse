package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.BonReceptionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BonReceptionJpaRepository extends JpaRepository<BonReceptionJpaEntity, UUID> {
    Optional<BonReceptionJpaEntity> findByIdAndCenterId(UUID id, UUID centerId);

    List<BonReceptionJpaEntity> findByCenterIdOrderByCreatedAtDesc(UUID centerId);
}

