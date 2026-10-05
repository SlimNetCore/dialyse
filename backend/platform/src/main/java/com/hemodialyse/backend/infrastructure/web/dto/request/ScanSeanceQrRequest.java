package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Scan d'un patient. {@code motifHorsPlanning} (RATTRAPAGE, URGENCE, AUTRE) et {@code precisionHorsPlanning}
 * confirment une séance d'un patient non programmé ce jour-là ; absents, le scan est refusé dans ce cas.
 */
public record ScanSeanceQrRequest(
        @NotNull UUID centerId,
        @NotBlank String qrCode,
        String motifHorsPlanning,
        @Size(max = 255) String precisionHorsPlanning
) {
}
