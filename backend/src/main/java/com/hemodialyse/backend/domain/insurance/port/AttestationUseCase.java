package com.hemodialyse.backend.domain.insurance.port;

import com.hemodialyse.backend.domain.insurance.model.AttestationDroit;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port In — Attestation use cases (domain-level).
 */
public interface AttestationUseCase {
    AttestationDroit create(CenterId centerId, UUID patientId, LocalDate dateDebut, LocalDate dateFin);
    List<AttestationDroit> listByPatient(CenterId centerId, UUID patientId);
}

