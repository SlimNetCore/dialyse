package com.hemodialyse.backend.infrastructure.web.dto.request.gmao;

import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO de requête pour créer une intervention GMAO
 */
public record CreateInterventionRequest(
        @NotNull(message = "ID équipement requis")
        UUID equipementId,

        @NotBlank(message = "Type d'intervention requis")
        String type,

        @NotNull(message = "Date de début requise")
        OffsetDateTime dateDebut,

        @NotBlank(message = "Description requise")
        String description,

        UUID intervenantId,

        @NotBlank(message = "État de l'équipement requis")
        String etatEquipementAvant,

        /** Panne constatée (symptôme), optionnelle. */
        String symptome,

        /** NORMALE (défaut), HAUTE ou URGENTE. */
        String priorite,

        /** Échéance de réalisation, optionnelle. */
        OffsetDateTime echeance
) {
    public CreateInterventionRequest(UUID equipementId, String type, OffsetDateTime dateDebut, String description,
                                     UUID intervenantId, String etatEquipementAvant) {
        this(equipementId, type, dateDebut, description, intervenantId, etatEquipementAvant, null, null, null);
    }
}

