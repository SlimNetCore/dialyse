package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.greffe.aggregate.DonneurVivant;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DonneurVivantResponse(
        UUID id,
        UUID patientId,
        UUID centerId,
        String nom,
        String prenom,
        LocalDate dateNaissance,
        String lienParente,
        String telephone,
        String groupeSanguin,
        String typageHla,
        String statutBilan,
        String crossmatchResultat,
        LocalDate dateCrossmatch,
        String bilanRealise,
        String contreIndications,
        String decisionFinale,
        LocalDate dateDecision,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static DonneurVivantResponse from(DonneurVivant d) {
        return new DonneurVivantResponse(
                d.getId(), d.getPatientId(), d.getCenterId(), d.getNom(), d.getPrenom(), d.getDateNaissance(),
                d.getLienParente().name(), d.getTelephone(), d.getGroupeSanguin(), d.getTypageHla(),
                d.getStatutBilan().name(), d.getCrossmatchResultat().name(), d.getDateCrossmatch(),
                d.getBilanRealise(), d.getContreIndications(), d.getDecisionFinale(), d.getDateDecision(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
