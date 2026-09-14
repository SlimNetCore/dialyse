package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.anemie.aggregate.AlerteObservance;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AlerteObservanceResponse(
        UUID id,
        UUID patientId,
        UUID centerId,
        String typeTraitement,
        String type,
        LocalDate periodeDebut,
        LocalDate periodeFin,
        int dosesAttendues,
        int dosesAdministrees,
        String message,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt
) {

    public static AlerteObservanceResponse from(AlerteObservance a) {
        return new AlerteObservanceResponse(
                a.getId(), a.getPatientId(), a.getCenterId(), a.getTypeTraitement().name(), a.getType().name(),
                a.getPeriodeDebut(), a.getPeriodeFin(), a.getDosesAttendues(), a.getDosesAdministrees(),
                a.getMessage(), a.getCreatedAt(), a.getResolvedAt().orElse(null));
    }
}
