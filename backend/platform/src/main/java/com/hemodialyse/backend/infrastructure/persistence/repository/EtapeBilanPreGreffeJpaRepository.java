package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.EtapeBilanPreGreffeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EtapeBilanPreGreffeJpaRepository extends JpaRepository<EtapeBilanPreGreffeJpaEntity, UUID> {

    List<EtapeBilanPreGreffeJpaEntity> findByPatientIdAndCenterIdOrderByCategorieAscLibelleAsc(
            UUID patientId, UUID centerId);

    Optional<EtapeBilanPreGreffeJpaEntity> findByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);

    void deleteByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);
}
