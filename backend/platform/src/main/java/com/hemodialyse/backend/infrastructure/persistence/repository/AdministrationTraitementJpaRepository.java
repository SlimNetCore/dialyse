package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.AdministrationTraitementJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdministrationTraitementJpaRepository extends JpaRepository<AdministrationTraitementJpaEntity, UUID> {

    Page<AdministrationTraitementJpaEntity> findByPatientIdAndCenterId(UUID patientId, UUID centerId, Pageable pageable);

    List<AdministrationTraitementJpaEntity> findByPatientIdAndCenterIdOrderByDateAdministrationDesc(
            UUID patientId, UUID centerId);
}
