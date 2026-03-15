package com.hemodialyse.backend.application.port.in;

import com.hemodialyse.backend.domain.insurance.model.AttestationDroit;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AttestationUseCase {
    AttestationDroit create(CenterId centerId, UUID patientId, LocalDate dateDebut, LocalDate dateFin);
    List<AttestationDroit> listByPatient(CenterId centerId, UUID patientId);
}

