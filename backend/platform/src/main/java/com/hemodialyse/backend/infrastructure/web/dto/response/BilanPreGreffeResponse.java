package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.greffe.aggregate.BilanPreGreffe;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BilanPreGreffeResponse(
        UUID id,
        UUID patientId,
        UUID centerId,
        String statut,
        LocalDate dateDebutBilan,
        LocalDate dateInscriptionListeAttente,
        LocalDate dateGreffe,
        String groupeSanguinConfirme,
        String typageHla,
        BigDecimal praClasseI,
        BigDecimal praClasseII,
        String contreIndications,
        String conclusionNephrologue,
        List<DecisionRcpResponse> decisionsRcp,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static BilanPreGreffeResponse from(BilanPreGreffe b) {
        return new BilanPreGreffeResponse(
                b.getId(), b.getPatientId(), b.getCenterId(), b.getStatut().name(), b.getDateDebutBilan(),
                b.getDateInscriptionListeAttente(), b.getDateGreffe(), b.getGroupeSanguinConfirme(),
                b.getTypageHla(), b.getPraClasseI(), b.getPraClasseII(), b.getContreIndications(),
                b.getConclusionNephrologue(),
                b.getDecisionsRcp().stream().map(DecisionRcpResponse::from).toList(),
                b.getCreatedAt(), b.getUpdatedAt());
    }
}
