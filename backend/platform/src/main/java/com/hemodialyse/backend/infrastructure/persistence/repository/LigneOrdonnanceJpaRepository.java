package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.LigneOrdonnanceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LigneOrdonnanceJpaRepository extends JpaRepository<LigneOrdonnanceJpaEntity, UUID> {

    List<LigneOrdonnanceJpaEntity> findByOrdonnanceId(UUID ordonnanceId);

    void deleteByOrdonnanceId(UUID ordonnanceId);
}
