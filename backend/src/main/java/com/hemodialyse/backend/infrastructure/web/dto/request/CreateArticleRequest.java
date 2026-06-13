package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateArticleRequest(
        @NotNull UUID centerId,
        @NotBlank String code,
        @NotBlank String libelle,
        @NotBlank String unite,
        BigDecimal seuilAlerte,
        boolean gereParLot
) {
}

