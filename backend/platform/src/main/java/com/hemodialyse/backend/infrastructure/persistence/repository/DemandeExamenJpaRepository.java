package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.DemandeExamenJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DemandeExamenJpaRepository extends JpaRepository<DemandeExamenJpaEntity, UUID> {

    Page<DemandeExamenJpaEntity> findByPatientIdAndCenterId(UUID patientId, UUID centerId, Pageable pageable);

    Optional<DemandeExamenJpaEntity> findByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);
}
