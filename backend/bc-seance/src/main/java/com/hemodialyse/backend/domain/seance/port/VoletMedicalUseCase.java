package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.VoletMedical;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.UUID;

public interface VoletMedicalUseCase {
    VoletMedical save(CenterId centerId,
                      UUID seanceId,
                      String prescription,
                      String toleranceSeance,
                      String examenClinique,
                      String resultatsBiologiques,
                      String ajustementsTherapeutiques,
                      String conclusionMedicale);
}

