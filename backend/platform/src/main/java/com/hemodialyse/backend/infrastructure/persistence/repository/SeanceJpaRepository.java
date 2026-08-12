package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.SeanceJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeanceJpaRepository extends JpaRepository<SeanceJpaEntity, UUID> {
    Optional<SeanceJpaEntity> findByIdAndCenterId(UUID id, UUID centerId);

    Optional<SeanceJpaEntity> findByCenterIdAndPatientIdAndDateSeance(UUID centerId, UUID patientId, LocalDate dateSeance);

    List<SeanceJpaEntity> findByCenterIdOrderByDateSeanceDescCreatedAtDesc(UUID centerId);

    Page<SeanceJpaEntity> findByCenterIdOrderByDateSeanceDescCreatedAtDesc(UUID centerId, Pageable pageable);

    Page<SeanceJpaEntity> findByCenterIdAndDateSeanceBetweenOrderByDateSeanceDescCreatedAtDesc(
            UUID centerId, LocalDate from, LocalDate to, Pageable pageable);
}



