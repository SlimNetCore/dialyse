package com.hemodialyse.backend.infrastructure.web.dto.response.gmao;

import com.hemodialyse.backend.domain.gmao.model.PlanMaintenance;
import com.hemodialyse.backend.domain.gmao.model.FrequenceMaintenance;
import com.hemodialyse.backend.domain.gmao.model.StatutPlan;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de réponse pour un plan de maintenance GMAO
 */
public record PlanMaintenanceResponse(
        UUID id,
        UUID equipementId,
        UUID centreId,
        String designation,
        String description,
        FrequenceMaintenance frequence,
        StatutPlan statut,
        LocalDateTime prochaineDatePrevue,
        LocalDateTime derniereDateExecution,
        Integer nombreExecutions,
        String tachesAEffectuer,
        LocalDateTime dateCreation,
        LocalDateTime dateModification,
        UUID creePar,
        UUID modifiePar
) {

    /**
     * Constructeur pour créer une réponse à partir d'un PlanMaintenance du domaine
     */
    public PlanMaintenanceResponse(PlanMaintenance plan) {
        this(
                plan.getId(),
                plan.getEquipementId(),
                plan.getCentreId(),
                plan.getDesignation(),
                plan.getDescription(),
                plan.getFrequence(),
                plan.getStatut(),
                plan.getProchaineDatePrevue(),
                plan.getDerniereDateExecution(),
                plan.getNombreExecutions(),
                plan.getTachemesAEffectuer(),
                plan.getDateCreation(),
                plan.getDateModification(),
                plan.getCreePar(),
                plan.getModifiePar()
        );
    }
}

