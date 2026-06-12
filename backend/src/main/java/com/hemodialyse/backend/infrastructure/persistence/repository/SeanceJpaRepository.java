package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.SeanceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SeanceJpaRepository extends JpaRepository<SeanceJpaEntity, UUID> {
    Optional<SeanceJpaEntity> findByIdAndCenterId(UUID id, UUID centerId);
}

