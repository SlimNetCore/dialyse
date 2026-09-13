package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.seance.model.AbordVasculaire;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Réponse « abord vasculaire » (FAV, PTFE, cathéter) de l'historique du patient.
 */
public record AbordVasculaireResponse(
        UUID id,
        UUID patientId,
        UUID centerId,
        String typeAbord,
        String cote,
        String localisation,
        LocalDate dateCreation,
        LocalDate dateFin,
        Boolean actif,
        String complications,
        OffsetDateTime createdAt
) {

    public static AbordVasculaireResponse from(AbordVasculaire a) {
        return new AbordVasculaireResponse(
                a.getId(),
                a.getPatientId(),
                a.getCenterId(),
                a.getTypeAbord(),
                a.getCote(),
                a.getLocalisation(),
                a.getDateCreation(),
                a.getDateFin(),
                a.getActif(),
                a.getComplications(),
                a.getCreatedAt()
        );
    }
}
