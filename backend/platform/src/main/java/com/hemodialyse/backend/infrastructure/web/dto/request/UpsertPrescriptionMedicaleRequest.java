package com.hemodialyse.backend.infrastructure.web.dto.request;

import com.hemodialyse.backend.domain.seance.model.UniteFrequence;

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
        UUID epoArticleId,
        Integer epoDoseUi,
        String epoVoie,
        Integer epoFrequenceValeur,
        UniteFrequence epoFrequenceUnite,
        UUID ferArticleId,
        Integer ferDoseMg,
        String ferVoie,
        Integer ferFrequenceValeur,
        UniteFrequence ferFrequenceUnite
) {
}
