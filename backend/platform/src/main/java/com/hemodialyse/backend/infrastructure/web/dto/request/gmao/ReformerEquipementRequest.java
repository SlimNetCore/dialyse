package com.hemodialyse.backend.infrastructure.web.dto.request.gmao;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO de requête pour réformer (mettre fin de vie, de façon définitive) un équipement GMAO.
 */
public record ReformerEquipementRequest(
        @NotBlank(message = "Motif de réforme requis")
        String motif
) {
}
