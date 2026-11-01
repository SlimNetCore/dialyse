package com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record PeriodeRequete(UUID centerId, @NotNull LocalDate from, @NotNull LocalDate to) {
}
