package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.DonneurVivantJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DonneurVivantJpaRepository extends JpaRepository<DonneurVivantJpaEntity, UUID> {

    List<DonneurVivantJpaEntity> findByPatientIdAndCenterIdOrderByCreatedAtDesc(UUID patientId, UUID centerId);

    Optional<DonneurVivantJpaEntity> findByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);

    void deleteByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);
}
