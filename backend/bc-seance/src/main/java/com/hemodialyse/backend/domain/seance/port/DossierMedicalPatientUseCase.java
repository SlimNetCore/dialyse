package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.DossierMedicalPatient;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DossierMedicalPatientUseCase {
    Optional<DossierMedicalPatient> getByPatient(CenterId centerId, UUID patientId);

    DossierMedicalPatient upsert(CenterId centerId,
                                 UUID patientId,
                                 String nephropathieInitiale,
                                 LocalDate dateMiseEnDialyse,
                                 String hepatiteBStatut,
                                 String hepatiteCStatut,
                                 String observationGlobale);
}

