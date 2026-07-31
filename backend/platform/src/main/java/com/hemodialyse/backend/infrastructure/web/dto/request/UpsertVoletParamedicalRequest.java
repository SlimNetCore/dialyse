package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record UpsertVoletParamedicalRequest(
        UUID centerId,
        BigDecimal poidsAvantKg,
        BigDecimal poidsApresKg,
        String taAvant,
        String taApres,
        Integer dureeMinutes,
        Integer debitSangMlMin,
        BigDecimal ultrafiltrationMl,
        String anticoagulant,
        String typeDialysat,
        String incidents
) {
}

