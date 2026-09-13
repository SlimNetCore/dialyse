package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.LocalDate;
import java.util.UUID;

public record UpsertAntecedentRequest(
        UUID centerId,
        String type,
        String codeSystem,
        String code,
        String codeDisplay,
        String libelleLibre,
        LocalDate dateDebut,
        LocalDate dateFin,
        String statutClinique,
        String severite,
        String note
) {
}
