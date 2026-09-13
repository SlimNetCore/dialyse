package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateOrdonnanceRequest(
        UUID centerId,
        String medecinId,
        LocalDate datePrescription,
        List<LigneOrdonnanceRequest> lignes
) {
}
