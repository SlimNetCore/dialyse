package com.hemodialyse.backend.domain.insurance.port;

import com.hemodialyse.backend.domain.insurance.model.AttestationDroit;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port Out — Attestation persistence.
 */
public interface AttestationRepositoryPort {
    AttestationDroit save(AttestationDroit a);
    void deleteById(UUID id);
    boolean existsValidAt(CenterId centerId, UUID patientId, LocalDate date);
    List<AttestationDroit> findByPatient(CenterId centerId, UUID patientId);
}

