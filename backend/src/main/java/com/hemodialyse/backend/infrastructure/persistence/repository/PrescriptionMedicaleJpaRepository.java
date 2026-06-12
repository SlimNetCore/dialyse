package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.PrescriptionMedicaleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PrescriptionMedicaleJpaRepository extends JpaRepository<PrescriptionMedicaleJpaEntity, UUID> {
    List<PrescriptionMedicaleJpaEntity> findByPatientIdAndCenterIdOrderByDatePrescriptionDesc(UUID patientId, UUID centerId);

    List<PrescriptionMedicaleJpaEntity> findByPatientIdAndCenterIdAndDatePrescriptionGreaterThanEqualOrderByDatePrescriptionDesc(
            UUID patientId, UUID centerId, LocalDate from);

    List<PrescriptionMedicaleJpaEntity> findByPatientIdAndCenterIdAndDatePrescriptionLessThanEqualOrderByDatePrescriptionDesc(
            UUID patientId, UUID centerId, LocalDate to);

    List<PrescriptionMedicaleJpaEntity> findByPatientIdAndCenterIdAndDatePrescriptionBetweenOrderByDatePrescriptionDesc(
            UUID patientId, UUID centerId, LocalDate from, LocalDate to);

    void deleteByIdAndPatientIdAndCenterId(UUID id, UUID patientId, UUID centerId);
}
