package com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ClotureRequete(UUID centerId, @Min(2000) @Max(3000) int annee,
                             @Min(1) @Max(12) int mois, @NotBlank String userId) {
}
