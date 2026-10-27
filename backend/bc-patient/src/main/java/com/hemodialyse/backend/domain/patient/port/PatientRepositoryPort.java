package com.hemodialyse.backend.domain.patient.port;

import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.Optional;

/**
 * Port Out — Patient persistence interface.
 * Implemented by infrastructure JPA adapter. Domain depends on this interface ONLY.
 */
public interface PatientRepositoryPort {
    Patient save(Patient patient);
    Optional<Patient> findById(PatientId id, CenterId centerId);

    Optional<Patient> findByCodePatient(CenterId centerId, String codePatient);
    Optional<Patient> findByNumeroAssurance(CenterId centerId, String numeroAssurance);
    List<Patient> findAllByCenter(CenterId centerId);
    long countByCenter(CenterId centerId);
}

