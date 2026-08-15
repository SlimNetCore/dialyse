package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.PeriodeComptableJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PeriodeComptableJpaRepository extends JpaRepository<PeriodeComptableJpaEntity, UUID> {
    Optional<PeriodeComptableJpaEntity> findByCenterIdAndAnneeAndMois(UUID centerId, int annee, int mois);
}

