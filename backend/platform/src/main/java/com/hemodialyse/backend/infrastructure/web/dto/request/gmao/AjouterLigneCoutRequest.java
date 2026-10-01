package com.hemodialyse.backend.infrastructure.web.dto.request.gmao;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO de requête pour ajouter une ligne de coût à une intervention GMAO.
 */
public record AjouterLigneCoutRequest(
        @NotBlank(message = "Type de ligne de coût requis")
        String type,

        @NotBlank(message = "Libellé requis")
        String libelle,

        @NotNull(message = "Quantité requise")
        @Positive(message = "Quantité invalide")
        BigDecimal quantite,

        @NotNull(message = "Prix unitaire requis")
        @DecimalMin(value = "0", inclusive = true, message = "Prix unitaire invalide")
        BigDecimal prixUnitaire,

        UUID articleStockId
) {
}
