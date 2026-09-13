package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateSerologieRequest(
        UUID centerId,
        String marqueur,
        String resultat,
        BigDecimal titre,
        String unite,
        LocalDate datePrelevement,
        String laboratoire,
        LocalDate dateProchainControle,
        String conduiteATenir
) {
}
