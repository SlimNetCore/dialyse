package com.hemodialyse.backend.infrastructure.web.dto.request;

import com.hemodialyse.backend.domain.seance.model.UniteFrequence;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
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
        @DecimalMin(value = "20.00", message = "Le poids sec cible doit être >= 20 kg")
        @DecimalMax(value = "300.00", message = "Le poids sec cible doit être <= 300 kg")
        BigDecimal poidsSecCibleKg,
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
