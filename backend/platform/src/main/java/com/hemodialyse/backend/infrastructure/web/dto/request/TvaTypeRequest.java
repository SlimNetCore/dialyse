package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TvaTypeRequest(
        @NotNull UUID centerId,
        @NotBlank String libelle,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal taux,
        @NotBlank String typePrestation,
        boolean exonere,
        @NotNull LocalDate dateDebutValidite,
        LocalDate dateFinValidite,
        String texteReference,
        @NotBlank String userId
) {
}

