package com.hemodialyse.backend.domain.facturation.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SeanceFacturationCandidate(
        UUID seanceId,
        UUID centerId,
        UUID patientId,
        LocalDate seanceDate,
        String seanceStatus,
        UUID existingFactureId,
        String patientCode,
        String patientNom,
        String patientPrenom,
        String patientStatus,
        String numeroImmatriculation,
        UUID centrePayeurId,
        UUID agenceId,
        String insuranceCode,
        UUID forfaitId,
        String forfaitLabel,
        BigDecimal forfaitPrixHt
) {
}

