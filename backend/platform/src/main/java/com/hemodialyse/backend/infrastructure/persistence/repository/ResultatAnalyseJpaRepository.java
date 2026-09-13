package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.ResultatAnalyseJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ResultatAnalyseJpaRepository extends JpaRepository<ResultatAnalyseJpaEntity, UUID>,
        JpaSpecificationExecutor<ResultatAnalyseJpaEntity> {
    List<ResultatAnalyseJpaEntity> findByPatientIdAndCenterIdOrderByDatePrelevementDesc(UUID patientId, UUID centerId);

    List<ResultatAnalyseJpaEntity> findByPatientIdAndCenterIdAndDatePrelevementGreaterThanEqualOrderByDatePrelevementDesc(
            UUID patientId, UUID centerId, LocalDate from);

    List<ResultatAnalyseJpaEntity> findByPatientIdAndCenterIdAndDatePrelevementLessThanEqualOrderByDatePrelevementDesc(
            UUID patientId, UUID centerId, LocalDate to);

    List<ResultatAnalyseJpaEntity> findByPatientIdAndCenterIdAndDatePrelevementBetweenOrderByDatePrelevementDesc(
            UUID patientId, UUID centerId, LocalDate from, LocalDate to);

    /**
     * Alimente le suivi de l'anémie (Phase 4) : dernières valeurs Hb/ferritine/CST/albumine connues.
     */
    List<ResultatAnalyseJpaEntity> findTop10ByPatientIdAndCenterIdOrderByDatePrelevementDesc(UUID patientId, UUID centerId);

    void deleteByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);
}

