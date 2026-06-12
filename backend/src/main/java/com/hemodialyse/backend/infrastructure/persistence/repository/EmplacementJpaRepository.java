package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.EmplacementJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmplacementJpaRepository extends JpaRepository<EmplacementJpaEntity, UUID> {
    Optional<EmplacementJpaEntity> findByIdAndCenterId(UUID id, UUID centerId);

    List<EmplacementJpaEntity> findByCenterIdAndActifTrueOrderByLibelleAsc(UUID centerId);
}

