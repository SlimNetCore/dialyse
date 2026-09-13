package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.ObservationBiologiqueJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ObservationBiologiqueJpaRepository extends JpaRepository<ObservationBiologiqueJpaEntity, UUID>,
        JpaSpecificationExecutor<ObservationBiologiqueJpaEntity> {

    List<ObservationBiologiqueJpaEntity> findByDemandeExamenIdAndCenterId(UUID demandeExamenId, UUID centerId);

    Optional<ObservationBiologiqueJpaEntity> findByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);

    void deleteByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);
}
