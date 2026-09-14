package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.medical.anemie.aggregate.AdministrationTraitement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdministrationTraitementResponse(
        UUID id,
        UUID patientId,
        UUID centerId,
        UUID prescriptionMedicaleId,
        String typeTraitement,
        String molecule,
        BigDecimal dose,
        String uniteDose,
        String voie,
        LocalDate dateAdministration,
        UUID seanceId,
        String administrePar,
        boolean administree,
        String motifNonAdministration,
        UUID articleId,
        BigDecimal quantiteArticle,
        OffsetDateTime createdAt
) {

    public static AdministrationTraitementResponse from(AdministrationTraitement a) {
        var dose = a.getDose().orElse(null);
        return new AdministrationTraitementResponse(
                a.getId(), a.getPatientId(), a.getCenterId(), a.getPrescriptionMedicaleId().orElse(null),
                a.getTypeTraitement().name(), a.getMolecule(), dose == null ? null : dose.valeur(),
                dose == null ? null : dose.unite(), a.getVoie(), a.getDateAdministration(),
                a.getSeanceId().orElse(null), a.getAdministrePar(), a.isAdministree(),
                a.getMotifNonAdministration(), a.getArticleId().orElse(null),
                a.getQuantiteArticle().orElse(null), a.getCreatedAt());
    }
}
