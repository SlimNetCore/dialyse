package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.BonSortieJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BonSortieJpaRepository extends JpaRepository<BonSortieJpaEntity, UUID> {
    Optional<BonSortieJpaEntity> findByIdAndCenterId(UUID id, UUID centerId);

    List<BonSortieJpaEntity> findByCenterIdOrderByCreatedAtDesc(UUID centerId);

    List<BonSortieJpaEntity> findByCenterIdAndSeanceId(UUID centerId, UUID seanceId);
}

