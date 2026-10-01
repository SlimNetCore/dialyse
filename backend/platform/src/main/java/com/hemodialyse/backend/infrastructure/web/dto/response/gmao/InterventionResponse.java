package com.hemodialyse.backend.infrastructure.web.dto.response.gmao;

import com.hemodialyse.backend.domain.gmao.model.Intervention;
import com.hemodialyse.backend.domain.gmao.model.PrioriteIntervention;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import com.hemodialyse.backend.domain.gmao.model.StatutIntervention;
import com.hemodialyse.backend.domain.gmao.model.TypeIntervention;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
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
        OffsetDateTime dateDebut,
        OffsetDateTime dateFin,
        UUID intervenantId,
        StatutEquipement etatEquipementAvant,
        StatutEquipement etatEquipementApres,
        PrioriteIntervention priorite,
        OffsetDateTime echeance,
        String symptome,
        String cause,
        boolean enRetard,
        boolean echeanceDepassee,
        String description,
        String actions,
        String pieceRemplacee,
        List<LigneCoutResponse> lignesCout,
        BigDecimal coutTotal,
        String observations,
        OffsetDateTime dateCreation,
        OffsetDateTime dateModification,
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
                intervention.getIntervenantId(),
                intervention.getEtatEquipementAvant(),
                intervention.getEtatEquipementApres(),
                intervention.getPriorite(),
                intervention.getEcheance(),
                intervention.getSymptome(),
                intervention.getCause(),
                intervention.enRetard(OffsetDateTime.now(ZoneOffset.UTC)),
                intervention.echeanceDepassee(OffsetDateTime.now(ZoneOffset.UTC)),
                intervention.getDescription(),
                intervention.getActions(),
                intervention.getPieceRemplacee(),
                intervention.getLignesCout().stream().map(LigneCoutResponse::new).toList(),
                intervention.coutTotal(),
                intervention.getObservations(),
                intervention.getDateCreation(),
                intervention.getDateModification(),
                intervention.getCreePar(),
                intervention.getModifiePar()
        );
    }
}
