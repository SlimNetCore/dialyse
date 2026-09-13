package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.serologie.aggregate.Serologie;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SerologieResponse(
        UUID id,
        UUID patientId,
        UUID centerId,
        String marqueur,
        String resultat,
        BigDecimal titre,
        String unite,
        LocalDate datePrelevement,
        String laboratoire,
        LocalDate dateProchainControle,
        String conduiteATenir,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static SerologieResponse from(Serologie s) {
        return new SerologieResponse(
                s.getId(), s.getPatientId(), s.getCenterId(), s.getMarqueur().name(), s.getResultat().name(),
                s.getTitre(), s.getUnite(), s.getDatePrelevement(), s.getLaboratoire(),
                s.getDateProchainControle(), s.getConduiteATenir(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
