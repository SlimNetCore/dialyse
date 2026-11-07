package com.hemodialyse.backend.contexts.seance.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Corps de {@code POST /api/v1/seances/scan} : mêmes champs JSON et mêmes contraintes que le legacy. {@code motifHorsPlanning}
 * (RATTRAPAGE, URGENCE, AUTRE) et {@code precisionHorsPlanning} confirment la séance d'un patient non attendu ce jour-là.
 */
public record ScanDeSeanceDemande(@NotNull UUID centerId, @NotBlank String qrCode, String motifHorsPlanning,
                                  @Size(max = 255) String precisionHorsPlanning) {
}
