package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.util.UUID;

public record UpsertVoletMedicalRequest(
        UUID centerId,
        String prescription,
        String toleranceSeance,
        String examenClinique,
        String resultatsBiologiques,
        String ajustementsTherapeutiques,
        String conclusionMedicale
) {
}

