package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * Corps de POST et PUT /intervenants : contrat inchangé par rapport au legacy (noms de champs {@code type} et
 * {@code tarifHoraireDefaut}, mêmes contraintes et mêmes messages). Aucune borne de longueur (KB-GMAO-27 : la base refuse,
 * 500 reproduit) ; les champs inconnus ({@code centreId}, {@code actif}, {@code id}) sont ignorés (KB-GMAO-29).
 */
public record IntervenantRequete(
        @NotBlank(message = "Nom requis")
        String nom,
        @NotBlank(message = "Type d'intervenant requis")
        String type,
        String telephone,
        String email,
        @DecimalMin(value = "0", inclusive = true, message = "Le tarif horaire ne peut pas être négatif")
        BigDecimal tarifHoraireDefaut) {
}
