package com.hemodialyse.backend.infrastructure.web.dto.request.gmao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de requête pour modifier les caractéristiques d'un équipement GMAO existant.
 * Le code, le type et la date d'installation sont structurants et restent immuables après création.
 */
public record UpdateEquipementRequest(
        @NotBlank(message = "Désignation requise")
        @Size(max = 255, message = "Désignation limitée à 255 caractères")
        String designation,

        String fabricant,

        String modele,

        String numeroSerie,

        String localisation
) {
}
