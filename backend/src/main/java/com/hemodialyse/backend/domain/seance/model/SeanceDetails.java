package com.hemodialyse.backend.domain.seance.model;

import com.hemodialyse.backend.domain.patient.model.Patient;

public record SeanceDetails(
        Seance seance,
        Patient patient,
        VoletParamedical voletParamedical,
        VoletMedical voletMedical
) {
}

