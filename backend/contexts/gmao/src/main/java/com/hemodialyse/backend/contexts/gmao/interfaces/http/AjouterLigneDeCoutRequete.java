package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/** Corps de POST /interventions/{id}/lignes-cout : contrat inchangé par rapport au legacy. */
public record AjouterLigneDeCoutRequete(
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

        UUID articleStockId) {
}
