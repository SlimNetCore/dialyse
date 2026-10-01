package com.hemodialyse.backend.infrastructure.web.dto.request.gmao;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

/**
 * DTO de requête pour créer un équipement GMAO
 */
public record CreateEquipementRequest(
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
        LocalDateTime dateInstallation,

        String localisation
) {
}

