package com.hemodialyse.backend.infrastructure.web.dto.response.gmao;

import com.hemodialyse.backend.domain.gmao.model.Equipement;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import com.hemodialyse.backend.domain.gmao.model.TypeEquipement;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO de réponse pour un équipement GMAO
 */
public record EquipementResponse(
        UUID id,
        String code,
        String designation,
        TypeEquipement type,
        String fabricant,
        String modele,
        String numeroSerie,
        OffsetDateTime dateInstallation,
        StatutEquipement statut,
        String localisation,
        String observations,
        OffsetDateTime dateCreation,
        OffsetDateTime dateModification,
        UUID creePar,
        UUID modifiePar,
        UUID salleId,
        BigDecimal prixAcquisition
) {

    /**
     * Constructeur pour créer une réponse à partir d'un Equipement du domaine
     */
    public EquipementResponse(Equipement equipement) {
        this(
                equipement.getId(),
                equipement.getCode(),
                equipement.getDesignation(),
                equipement.getType(),
                equipement.getFabricant(),
                equipement.getModele(),
                equipement.getNumeroSerie(),
                equipement.getDateInstallation(),
                equipement.getStatut(),
                equipement.getLocalisation(),
                equipement.getObservations(),
                equipement.getDateCreation(),
                equipement.getDateModification(),
                equipement.getCreePar(),
                equipement.getModifiePar(),
                equipement.getSalleId(),
                equipement.getPrixAcquisition()
        );
    }
}
