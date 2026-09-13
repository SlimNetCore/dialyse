package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.AntecedentJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AntecedentJpaRepository extends JpaRepository<AntecedentJpaEntity, UUID> {

    List<AntecedentJpaEntity> findByPatientIdAndCenterIdOrderByDateDebutDesc(UUID patientId, UUID centerId);

    Page<AntecedentJpaEntity> findByPatientIdAndCenterId(UUID patientId, UUID centerId, Pageable pageable);

    Optional<AntecedentJpaEntity> findByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);

    void deleteByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);
}
