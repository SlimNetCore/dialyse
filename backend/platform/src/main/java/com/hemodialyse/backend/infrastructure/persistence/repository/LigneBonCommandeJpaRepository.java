package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.LigneBonCommandeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LigneBonCommandeJpaRepository extends JpaRepository<LigneBonCommandeJpaEntity, UUID> {
    List<LigneBonCommandeJpaEntity> findByBonCommandeId(UUID bonCommandeId);

    void deleteByBonCommandeId(UUID bonCommandeId);
}

