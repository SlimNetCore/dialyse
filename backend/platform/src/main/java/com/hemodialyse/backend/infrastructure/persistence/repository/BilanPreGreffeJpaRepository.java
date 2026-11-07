package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.BilanPreGreffeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BilanPreGreffeJpaRepository extends JpaRepository<BilanPreGreffeJpaEntity, UUID> {

    Optional<BilanPreGreffeJpaEntity> findByPatientIdAndCenterId(UUID patientId, UUID centerId);

    /** DM7 (KB-MED-90) : lecture robuste d'un doublon existant, le plus ancien en premier (jamais de résultat non unique). */
    List<BilanPreGreffeJpaEntity> findByPatientIdAndCenterIdOrderByCreatedAtAscIdAsc(UUID patientId, UUID centerId);
}
