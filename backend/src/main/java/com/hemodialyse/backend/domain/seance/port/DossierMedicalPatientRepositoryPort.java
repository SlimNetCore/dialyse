package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.DossierMedicalPatient;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.Optional;
import java.util.UUID;

public interface DossierMedicalPatientRepositoryPort {
    Optional<DossierMedicalPatient> findByPatientId(UUID patientId, CenterId centerId);

    DossierMedicalPatient save(DossierMedicalPatient dossier);
}

