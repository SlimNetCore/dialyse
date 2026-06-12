package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.AbordVasculaire;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.UUID;

public interface AbordVasculaireRepositoryPort {
    List<AbordVasculaire> findByPatientId(UUID patientId, CenterId centerId);

    AbordVasculaire save(AbordVasculaire abord);
}

