package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.RegleTVAJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegleTVAJpaRepository extends JpaRepository<RegleTVAJpaEntity, UUID> {
    List<RegleTVAJpaEntity> findByCenterIdOrderByDateDebutValiditeDesc(UUID centerId);

    Optional<RegleTVAJpaEntity> findTopByCenterIdAndTypePrestationAndDateDebutValiditeLessThanEqualAndDateFinValiditeGreaterThanEqualOrderByDateDebutValiditeDesc(
            UUID centerId, String typePrestation, LocalDate date, LocalDate date2);

    Optional<RegleTVAJpaEntity> findTopByCenterIdAndTypePrestationAndDateDebutValiditeLessThanEqualAndDateFinValiditeIsNullOrderByDateDebutValiditeDesc(
            UUID centerId, String typePrestation, LocalDate date);
}

