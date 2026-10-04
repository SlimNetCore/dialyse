package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.PatientJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientJpaRepository extends JpaRepository<PatientJpaEntity, UUID>, JpaSpecificationExecutor<PatientJpaEntity> {
    Optional<PatientJpaEntity> findByCenterIdAndNumeroAssurance(UUID centerId, String numeroAssurance);

    Optional<PatientJpaEntity> findByCenterIdAndCodePatient(UUID centerId, String codePatient);
    Optional<PatientJpaEntity> findByIdAndCenterId(UUID id, UUID centerId);
    List<PatientJpaEntity> findByCenterId(UUID centerId);
    long countByCenterId(UUID centerId);

    @Query("select p from PatientJpaEntity p where p.centerId = :centerId "
            + "and (p.salleId is not null or p.positionId is not null or p.generateurId is not null)")
    List<PatientJpaEntity> findWithAssignment(@Param("centerId") UUID centerId);

    @Query("select distinct p.centerId from PatientJpaEntity p "
            + "where p.salleId is not null or p.positionId is not null or p.generateurId is not null")
    List<UUID> findCenterIdsWithAssignment();
}

