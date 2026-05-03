package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.PecJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PecJpaRepository extends JpaRepository<PecJpaEntity, UUID>, JpaSpecificationExecutor<PecJpaEntity> {
    Optional<PecJpaEntity> findByIdAndCenterId(UUID id, UUID centerId);
    List<PecJpaEntity> findByCenterIdAndPatientId(UUID centerId, UUID patientId);
    List<PecJpaEntity> findByCenterId(UUID centerId);

    List<PecJpaEntity> findByCenterIdAndStatutAndPatientIdIn(UUID centerId, String statut, Collection<UUID> patientIds);
}

