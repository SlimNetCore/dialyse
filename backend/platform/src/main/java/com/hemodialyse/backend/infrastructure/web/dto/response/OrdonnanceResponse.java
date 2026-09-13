package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.ordonnance.aggregate.Ordonnance;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrdonnanceResponse(
        UUID id,
        UUID patientId,
        UUID centerId,
        String medecinId,
        LocalDate datePrescription,
        String statut,
        String numero,
        List<LigneOrdonnanceResponse> lignes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime signedAt
) {

    public static OrdonnanceResponse from(Ordonnance o) {
        return new OrdonnanceResponse(
                o.getId(), o.getPatientId(), o.getCenterId(), o.getMedecinId(), o.getDatePrescription(),
                o.getStatut().name(), o.getNumero(),
                o.getLignes().stream().map(LigneOrdonnanceResponse::from).toList(),
                o.getCreatedAt(), o.getUpdatedAt(), o.getSignedAt());
    }
}
