package com.hemodialyse.backend.infrastructure.web.dto.request.gmao;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * DTO de requête pour créer un intervenant GMAO (technicien interne / prestataire externe).
 */
public record CreateIntervenantRequest(
        @NotBlank(message = "Nom requis")
        String nom,

        @NotBlank(message = "Type d'intervenant requis")
        String type,

        String telephone,

        String email,

        @DecimalMin(value = "0", inclusive = true, message = "Le tarif horaire ne peut pas être négatif")
        BigDecimal tarifHoraireDefaut
) {
}
