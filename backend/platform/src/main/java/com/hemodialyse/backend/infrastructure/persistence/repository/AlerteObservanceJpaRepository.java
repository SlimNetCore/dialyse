package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.AlerteObservanceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlerteObservanceJpaRepository extends JpaRepository<AlerteObservanceJpaEntity, UUID> {

    List<AlerteObservanceJpaEntity> findByPatientIdAndCenterIdOrderByCreatedAtDesc(UUID patientId, UUID centerId);

    Optional<AlerteObservanceJpaEntity> findByPatientIdAndCenterIdAndTypeTraitementAndTypeAlerteAndResolvedAtIsNull(
            UUID patientId, UUID centerId, String typeTraitement, String typeAlerte);

    Optional<AlerteObservanceJpaEntity> findByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);
}
