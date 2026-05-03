package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.AttestationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AttestationJpaRepository extends JpaRepository<AttestationJpaEntity, UUID>, JpaSpecificationExecutor<AttestationJpaEntity> {
    List<AttestationJpaEntity> findByCenterIdAndPatientId(UUID centerId, UUID patientId);

    @Query("select case when count(a) > 0 then true else false end from AttestationJpaEntity a " +
           "where a.centerId = :centerId and a.patientId = :patientId and :atDate between a.dateDebut and a.dateFin")
    boolean existsValidAt(UUID centerId, UUID patientId, LocalDate atDate);
}

