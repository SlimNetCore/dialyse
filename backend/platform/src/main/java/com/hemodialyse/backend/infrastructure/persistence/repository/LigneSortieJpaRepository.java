package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.LigneSortieJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LigneSortieJpaRepository extends JpaRepository<LigneSortieJpaEntity, UUID> {
    List<LigneSortieJpaEntity> findByBonSortieId(UUID bonSortieId);

    void deleteByBonSortieId(UUID bonSortieId);
}

