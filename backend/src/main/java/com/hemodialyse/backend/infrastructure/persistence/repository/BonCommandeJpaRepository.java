package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.BonCommandeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BonCommandeJpaRepository extends JpaRepository<BonCommandeJpaEntity, UUID> {
    Optional<BonCommandeJpaEntity> findByIdAndCenterId(UUID id, UUID centerId);

    List<BonCommandeJpaEntity> findByCenterIdOrderByCreatedAtDesc(UUID centerId);
}

