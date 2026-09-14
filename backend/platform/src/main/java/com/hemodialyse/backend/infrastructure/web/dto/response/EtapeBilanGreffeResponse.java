package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.greffe.aggregate.EtapeBilanPreGreffe;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EtapeBilanGreffeResponse(
        UUID id,
        UUID patientId,
        UUID centerId,
        String categorie,
        String libelle,
        String statut,
        LocalDate dateRealisation,
        String resultat,
        LocalDate dateExpiration,
        UUID demandeExamenId,
        UUID serologieId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static EtapeBilanGreffeResponse from(EtapeBilanPreGreffe e) {
        return new EtapeBilanGreffeResponse(
                e.getId(), e.getPatientId(), e.getCenterId(), e.getCategorie().name(), e.getLibelle(),
                e.getStatut().name(), e.getDateRealisation(), e.getResultat(), e.getDateExpiration(),
                e.getDemandeExamenId(), e.getSerologieId(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
