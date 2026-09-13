package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.antecedent.aggregate.Antecedent;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AntecedentResponse(
        UUID id,
        UUID patientId,
        UUID centerId,
        String type,
        String codeSystem,
        String code,
        String codeDisplay,
        String libelleLibre,
        LocalDate dateDebut,
        LocalDate dateFin,
        String statutClinique,
        String severite,
        String note,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static AntecedentResponse from(Antecedent a) {
        ConceptCode diagnostic = a.getDiagnostic();
        return new AntecedentResponse(
                a.getId(), a.getPatientId(), a.getCenterId(), a.getType().name(),
                diagnostic == null ? null : diagnostic.system().name(),
                diagnostic == null ? null : diagnostic.code(),
                diagnostic == null ? null : diagnostic.display(),
                a.getLibelleLibre(), a.getPeriode().debut(), a.getPeriode().fin(),
                a.getStatutClinique().name(), a.getSeverite(), a.getNote(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
