package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.FournisseurJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FournisseurJpaRepository extends JpaRepository<FournisseurJpaEntity, UUID> {
    Optional<FournisseurJpaEntity> findByIdAndCenterId(UUID id, UUID centerId);

    List<FournisseurJpaEntity> findByCenterIdAndActifTrueOrderByRaisonSocialeAsc(UUID centerId);

    List<FournisseurJpaEntity> findByCenterIdAndActifTrueAndRaisonSocialeContainingIgnoreCaseOrderByRaisonSocialeAsc(
            UUID centerId, String query);
}

