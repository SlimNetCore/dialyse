package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.seance.model.DossierMedicalPatient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Réponse « dossier médical de base » du patient.
 * <p>
 * Remplace les {@code LinkedHashMap} construits à la main : le contrat devient explicite,
 * typé et vérifiable à la compilation — indispensable sur un module de cette taille.
 */
public record DossierMedicalPatientResponse(
        UUID id,
        UUID patientId,
        UUID centerId,
        String nephropathieInitiale,
        LocalDate dateMiseEnDialyse,
        String hepatiteBStatut,
        String hepatiteCStatut,
        String observationGlobale,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static DossierMedicalPatientResponse from(DossierMedicalPatient d) {
        return new DossierMedicalPatientResponse(
                d.getId(),
                d.getPatientId(),
                d.getCenterId(),
                d.getNephropathieInitiale(),
                d.getDateMiseEnDialyse(),
                d.getHepatiteBStatut(),
                d.getHepatiteCStatut(),
                d.getObservationGlobale(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }
}
