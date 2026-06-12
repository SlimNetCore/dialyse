package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.LocalDate;
import java.util.UUID;

public record UpsertPrescriptionMedicaleRequest(
        UUID centerId,
        LocalDate datePrescription,
        UUID medecinId,
        Integer qbCible,
        Integer qdCible,
        Integer ufMaxMl,
        Integer dureeCibleMin,
        String typeDialyseurPrescrit,
        String anticoagTypePrescrit,
        String epoMolecule,
        Integer epoDoseUi,
        String epoVoie,
        String epoFrequence,
        String ferMolecule,
        Integer ferDoseMg,
        String ferVoie,
        String ferFrequence
) {
}

