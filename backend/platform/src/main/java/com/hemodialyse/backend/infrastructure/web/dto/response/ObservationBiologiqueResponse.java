package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.observation.aggregate.ObservationBiologique;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ObservationBiologiqueResponse(
        UUID id,
        UUID patientId,
        UUID centerId,
        UUID demandeExamenId,
        String codeSystem,
        String code,
        String codeDisplay,
        BigDecimal valeurNum,
        String unite,
        String valeurTexte,
        LocalDate datePrelevement,
        String statut,
        String source,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static ObservationBiologiqueResponse from(ObservationBiologique o) {
        var analyte = o.getAnalyte();
        var valeur = o.getValeurNum().orElse(null);
        return new ObservationBiologiqueResponse(
                o.getId(), o.getPatientId(), o.getCenterId(), o.getDemandeExamenId().orElse(null),
                analyte.system().name(), analyte.code(), analyte.display(),
                valeur == null ? null : valeur.valeur(), valeur == null ? null : valeur.unite(),
                o.getValeurTexte(), o.getDatePrelevement(), o.getStatut().name(), o.getSource().name(),
                o.getCreatedAt(), o.getUpdatedAt());
    }
}
