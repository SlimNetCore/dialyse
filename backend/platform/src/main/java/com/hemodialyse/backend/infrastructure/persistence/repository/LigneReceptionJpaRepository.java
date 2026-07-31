package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.LigneReceptionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LigneReceptionJpaRepository extends JpaRepository<LigneReceptionJpaEntity, UUID> {
    List<LigneReceptionJpaEntity> findByBonReceptionId(UUID bonReceptionId);

    void deleteByBonReceptionId(UUID bonReceptionId);
}

