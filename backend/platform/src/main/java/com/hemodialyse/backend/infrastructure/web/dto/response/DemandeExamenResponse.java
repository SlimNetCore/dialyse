package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.examen.aggregate.DemandeExamen;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DemandeExamenResponse(
        UUID id,
        UUID patientId,
        UUID centerId,
        String prescripteurId,
        LocalDate dateDemande,
        String categorie,
        boolean urgent,
        String motif,
        String statut,
        String conclusion,
        List<LigneDemandeExamenResponse> lignes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static DemandeExamenResponse from(DemandeExamen d) {
        return new DemandeExamenResponse(
                d.getId(), d.getPatientId(), d.getCenterId(), d.getPrescripteurId(), d.getDateDemande(),
                d.getCategorie().name(), d.isUrgent(), d.getMotif(), d.getStatut().name(), d.getConclusion(),
                d.getLignes().stream().map(LigneDemandeExamenResponse::from).toList(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
