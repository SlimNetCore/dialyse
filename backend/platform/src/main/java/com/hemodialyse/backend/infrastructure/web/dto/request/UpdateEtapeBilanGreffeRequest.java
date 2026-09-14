package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateEtapeBilanGreffeRequest(
        UUID centerId,
        String statut,
        LocalDate dateRealisation,
        String resultat,
        LocalDate dateExpiration,
        UUID demandeExamenId,
        UUID serologieId
) {
}
