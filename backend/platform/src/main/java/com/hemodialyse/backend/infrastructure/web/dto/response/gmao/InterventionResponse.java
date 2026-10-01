package com.hemodialyse.backend.infrastructure.web.dto.response.gmao;

import com.hemodialyse.backend.domain.gmao.model.Intervention;
import com.hemodialyse.backend.domain.gmao.model.StatutIntervention;
import com.hemodialyse.backend.domain.gmao.model.TypeIntervention;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO de réponse pour une intervention GMAO
 */
public record InterventionResponse(
        UUID id,
        UUID equipementId,
        UUID centreId,
        TypeIntervention type,
        StatutIntervention statut,
        LocalDateTime dateDebut,
        LocalDateTime dateFin,
        UUID technicien,
        String description,
        String actions,
        String pieceRemplacee,
        BigDecimal cout,
        String observations,
        LocalDateTime dateCreation,
        LocalDateTime dateModification,
        UUID creePar,
        UUID modifiePar
) {

    /**
     * Constructeur pour créer une réponse à partir d'une Intervention du domaine
     */
    public InterventionResponse(Intervention intervention) {
        this(
                intervention.getId(),
                intervention.getEquipementId(),
                intervention.getCentreId(),
                intervention.getType(),
                intervention.getStatut(),
                intervention.getDateDebut(),
                intervention.getDateFin(),
                intervention.getTechnicien(),
                intervention.getDescription(),
                intervention.getActions(),
                intervention.getPieceRemplacee(),
                intervention.getCout(),
                intervention.getObservations(),
                intervention.getDateCreation(),
                intervention.getDateModification(),
                intervention.getCreePar(),
                intervention.getModifiePar()
        );
    }
}

