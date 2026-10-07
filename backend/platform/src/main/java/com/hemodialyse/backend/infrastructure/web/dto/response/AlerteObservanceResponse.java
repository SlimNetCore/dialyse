package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.anemie.aggregate.AlerteObservance;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Alerte d'observance. {@code dosesAttendues} et {@code dosesAdministrees} sont exprimées dans {@code uniteDose} quand
 * elle est renseignée (quantités de dose), sinon ce sont des nombres d'administrations ; {@code dosePrescrite},
 * {@code frequenceValeur} et {@code frequenceUnite} décrivent la prescription qui explique l'alerte.
 */
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
        String uniteDose,
        Integer dosePrescrite,
        Integer frequenceValeur,
        String frequenceUnite,
        String message,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt
) {

    public static AlerteObservanceResponse from(AlerteObservance a) {
        return new AlerteObservanceResponse(
                a.getId(), a.getPatientId(), a.getCenterId(), a.getTypeTraitement().name(), a.getType().name(),
                a.getPeriodeDebut(), a.getPeriodeFin(), a.getDosesAttendues(), a.getDosesAdministrees(),
                a.getDetail().uniteDose(), a.getDetail().dosePrescrite(), a.getDetail().frequenceValeur(),
                a.getDetail().frequenceUnite(), a.getMessage(), a.getCreatedAt(), a.getResolvedAt().orElse(null));
    }
}
