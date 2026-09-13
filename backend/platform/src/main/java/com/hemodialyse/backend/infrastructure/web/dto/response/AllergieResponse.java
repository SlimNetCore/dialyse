package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.allergie.aggregate.Allergie;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AllergieResponse(
        UUID id,
        UUID patientId,
        UUID centerId,
        String codeSystem,
        String code,
        String codeDisplay,
        String categorie,
        String criticite,
        String typeReaction,
        String manifestations,
        LocalDate dateConstatation,
        String statutVerification,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static AllergieResponse from(Allergie a) {
        return new AllergieResponse(
                a.getId(), a.getPatientId(), a.getCenterId(),
                a.getSubstance().system().name(), a.getSubstance().code(), a.getSubstance().display(),
                a.getCategorie().name(), a.getCriticite().name(), a.getTypeReaction().name(),
                a.getManifestations(), a.getDateConstatation(), a.getStatutVerification().name(),
                a.getCreatedAt(), a.getUpdatedAt());
    }
}
