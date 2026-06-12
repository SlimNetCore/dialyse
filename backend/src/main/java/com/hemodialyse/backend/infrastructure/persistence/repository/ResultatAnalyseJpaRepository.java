package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.ResultatAnalyseJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ResultatAnalyseJpaRepository extends JpaRepository<ResultatAnalyseJpaEntity, UUID> {
    List<ResultatAnalyseJpaEntity> findByPatientIdAndCenterIdOrderByDatePrelevementDesc(UUID patientId, UUID centerId);

    List<ResultatAnalyseJpaEntity> findByPatientIdAndCenterIdAndDatePrelevementGreaterThanEqualOrderByDatePrelevementDesc(
            UUID patientId, UUID centerId, LocalDate from);

    List<ResultatAnalyseJpaEntity> findByPatientIdAndCenterIdAndDatePrelevementLessThanEqualOrderByDatePrelevementDesc(
            UUID patientId, UUID centerId, LocalDate to);

    List<ResultatAnalyseJpaEntity> findByPatientIdAndCenterIdAndDatePrelevementBetweenOrderByDatePrelevementDesc(
            UUID patientId, UUID centerId, LocalDate from, LocalDate to);

    void deleteByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);
}

