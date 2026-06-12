package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.AbordVasculaireJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AbordVasculaireJpaRepository extends JpaRepository<AbordVasculaireJpaEntity, UUID> {
    List<AbordVasculaireJpaEntity> findByPatientIdAndCenterIdOrderByDateCreationDesc(UUID patientId, UUID centerId);
}

