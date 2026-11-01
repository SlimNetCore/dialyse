package com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PieceRequete(@NotNull UUID modeleId, @NotNull LocalDate date, @Size(max = 255) String libelle,
                           @NotEmpty List<BigDecimal> montants) {
}
