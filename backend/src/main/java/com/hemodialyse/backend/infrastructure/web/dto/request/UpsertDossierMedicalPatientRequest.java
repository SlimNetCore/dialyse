package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.LocalDate;
import java.util.UUID;

public record UpsertDossierMedicalPatientRequest(
        UUID centerId,
        String nephropathieInitiale,
        LocalDate dateMiseEnDialyse,
        String hepatiteBStatut,
        String hepatiteCStatut,
        String observationGlobale
) {
}

