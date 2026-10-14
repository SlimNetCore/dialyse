package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Corps de POST /equipements : contrat inchangé par rapport au legacy (noms de champs, contraintes et messages). Seuls
 * {@code code} (50) et {@code designation} (255) sont bornés ; les autres longueurs sont refusées par la base (500,
 * KB-GMAO-35). Les champs inconnus ({@code id}, {@code statut}, {@code centreId}, {@code creePar}) sont ignorés.
 */
public record EnregistrerEquipementRequete(
        @NotBlank(message = "Code requis")
        @Size(max = 50, message = "Code limité à 50 caractères")
        String code,

        @NotBlank(message = "Désignation requise")
        @Size(max = 255, message = "Désignation limitée à 255 caractères")
        String designation,

        @NotBlank(message = "Type d'équipement requis")
        String type,

        String fabricant,

        String modele,

        String numeroSerie,

        @NotNull(message = "Date d'installation requise")
        OffsetDateTime dateInstallation,

        String localisation,

        UUID salleId,

        @DecimalMin(value = "0", inclusive = true, message = "Le prix d'acquisition ne peut pas être négatif")
        BigDecimal prixAcquisition) {
}
