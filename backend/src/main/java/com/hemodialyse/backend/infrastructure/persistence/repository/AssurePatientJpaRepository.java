package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.AssurePatientId;
import com.hemodialyse.backend.infrastructure.persistence.entity.AssurePatientJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssurePatientJpaRepository extends JpaRepository<AssurePatientJpaEntity, AssurePatientId> {
    @Modifying
    @Query("update AssurePatientJpaEntity ap set ap.isPrimary = false where ap.centerId = :centerId and ap.patientId = :patientId")
    void clearPrimary(UUID centerId, UUID patientId);

    @Modifying
    @Query("update AssurePatientJpaEntity ap set ap.isPrimary = false, ap.dateFinAffectation = :endDate where ap.centerId = :centerId and ap.patientId = :patientId and ap.isPrimary = true and ap.dateFinAffectation is null")
    void closePrimary(UUID centerId, UUID patientId, LocalDate endDate);

    Optional<AssurePatientJpaEntity> findFirstByCenterIdAndPatientIdAndIsPrimaryTrueOrderByDateAffectationDesc(UUID centerId, UUID patientId);

    List<AssurePatientJpaEntity> findByCenterIdAndPatientIdOrderByDateAffectationDesc(UUID centerId, UUID patientId);
}


