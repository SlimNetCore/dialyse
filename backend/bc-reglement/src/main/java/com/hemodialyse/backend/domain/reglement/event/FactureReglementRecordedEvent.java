package com.hemodialyse.backend.domain.reglement.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FactureReglementRecordedEvent(
        UUID factureId,
        UUID centerId,
        BigDecimal montant,
        LocalDate dateReglement,
        String userId
) {
}

