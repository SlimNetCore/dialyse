package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.DossierMedicalPatientJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DossierMedicalPatientJpaRepository extends JpaRepository<DossierMedicalPatientJpaEntity, UUID> {
    Optional<DossierMedicalPatientJpaEntity> findByPatientIdAndCenterId(UUID patientId, UUID centerId);
}

