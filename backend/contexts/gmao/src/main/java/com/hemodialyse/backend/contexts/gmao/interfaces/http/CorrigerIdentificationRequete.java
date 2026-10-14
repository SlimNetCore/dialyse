package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Corps de PUT /equipements/{id} : contrat inchangé par rapport au legacy. Remplacement total (KB-GMAO-36) : ce qui est omis
 * est effacé. Code, type, date d'installation et statut du corps sont ignorés (R5).
 */
public record CorrigerIdentificationRequete(
        @NotBlank(message = "Désignation requise")
        @Size(max = 255, message = "Désignation limitée à 255 caractères")
        String designation,

        String fabricant,

        String modele,

        String numeroSerie,

        String localisation,

        UUID salleId,

        @DecimalMin(value = "0", inclusive = true, message = "Le prix d'acquisition ne peut pas être négatif")
        BigDecimal prixAcquisition) {
}
