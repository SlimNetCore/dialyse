package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.MappingComptableJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MappingComptableJpaRepository extends JpaRepository<MappingComptableJpaEntity, UUID> {
    Optional<MappingComptableJpaEntity> findByCenterId(UUID centerId);
}

