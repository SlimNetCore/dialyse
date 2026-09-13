package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateObservationRequest(
        UUID centerId,
        UUID demandeExamenId,
        String codeSystem,
        String code,
        String codeDisplay,
        BigDecimal valeurNum,
        String unite,
        String valeurTexte,
        LocalDate datePrelevement,
        String statut
) {
}
