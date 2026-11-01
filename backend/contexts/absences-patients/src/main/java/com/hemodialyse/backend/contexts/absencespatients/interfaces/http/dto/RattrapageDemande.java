package com.hemodialyse.backend.contexts.absencespatients.interfaces.http.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RattrapageDemande(@NotNull LocalDate dateRattrapage) {
}
