package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateAdministrationTraitementRequest(
        UUID centerId,
        UUID prescriptionMedicaleId,
        String typeTraitement,
        String molecule,
        BigDecimal dose,
        String uniteDose,
        String voie,
        LocalDate dateAdministration,
        UUID seanceId,
        String administrePar,
        boolean administree,
        String motifNonAdministration
) {
}
